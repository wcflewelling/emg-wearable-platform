/*
 * nRF54L15 — Dual-Channel ADC (2 kHz) + on-die TEMP sensor over BLE
 *
 * No external sensor — the "Sensor" characteristic reports the nRF54L15's
 * internal die temperature (via the TEMP peripheral / nrfx HAL). The
 * humidity field is retained in the packet layout but always reports 0.0.
 */

#include <zephyr/kernel.h>
#include <zephyr/device.h>
#include <zephyr/drivers/adc.h>
#include <zephyr/bluetooth/bluetooth.h>
#include <zephyr/bluetooth/hci.h>
#include <zephyr/bluetooth/conn.h>
#include <zephyr/bluetooth/uuid.h>
#include <zephyr/bluetooth/gatt.h>
#include <zephyr/logging/log.h>
#include <hal/nrf_temp.h>
#include <string.h>

LOG_MODULE_REGISTER(wyatt_ble, LOG_LEVEL_INF);

#define SERVICE_UUID \
    BT_UUID_DECLARE_128(BT_UUID_128_ENCODE(0x75ab9000,0xcd33,0x44dd,0xba7e,0x2037855136d4))
#define ADC_CHAR_UUID \
    BT_UUID_DECLARE_128(BT_UUID_128_ENCODE(0x27314856,0xfe77,0x45f0,0xb4f8,0xfed858ece431))
#define SENSOR_CHAR_UUID \
    BT_UUID_DECLARE_128(BT_UUID_128_ENCODE(0x27314857,0xfe77,0x45f0,0xb4f8,0xfed858ece431))

/* ── ADC ── */
static const struct adc_dt_spec adc_ch1 =
    ADC_DT_SPEC_GET_BY_IDX(DT_PATH(zephyr_user), 0);
static const struct adc_dt_spec adc_ch2 =
    ADC_DT_SPEC_GET_BY_IDX(DT_PATH(zephyr_user), 1);

/* Must match Android BATCH_SAMPLES. Packet size = 4 + BATCH_SIZE*4.
 * 8 samples → 36 bytes/packet; requires ATT MTU ≥ 39. */
#define BATCH_SIZE  8
#define BUF_BYTES   (4 + BATCH_SIZE * 4)   /* 36 bytes */
static uint8_t  adc_buf[BUF_BYTES];
static int      sample_count;
static uint32_t batch_start_us;
static int16_t  raw_ch1;
static int16_t  raw_ch2;

static struct adc_sequence seq_ch1 = {
    .buffer      = &raw_ch1,
    .buffer_size = sizeof(raw_ch1),
};
static struct adc_sequence seq_ch2 = {
    .buffer      = &raw_ch2,
    .buffer_size = sizeof(raw_ch2),
};

/* ── On-die TEMP (nrfx HAL) ──
 * nRF54L15 TEMP peripheral. Result is a signed count in 0.25 °C steps.
 * Bluetooth is running so HFCLK is active; TEMP needs no extra config. */
static bool nrf_temp_read(float *temp_c)
{
    nrf_temp_event_clear(NRF_TEMP, NRF_TEMP_EVENT_DATARDY);
    nrf_temp_task_trigger(NRF_TEMP, NRF_TEMP_TASK_START);

    /* Conversion is a few µs on nRF54L; cap wait at 10 ms. */
    for (int i = 0; i < 1000; i++) {
        if (nrf_temp_event_check(NRF_TEMP, NRF_TEMP_EVENT_DATARDY)) {
            nrf_temp_event_clear(NRF_TEMP, NRF_TEMP_EVENT_DATARDY);
            nrf_temp_task_trigger(NRF_TEMP, NRF_TEMP_TASK_STOP);
            int32_t raw = nrf_temp_result_get(NRF_TEMP);
            *temp_c = (float)raw * 0.25f;
            return true;
        }
        k_busy_wait(10);
    }
    nrf_temp_task_trigger(NRF_TEMP, NRF_TEMP_TASK_STOP);
    return false;
}

/* ── BLE ── */
static struct bt_conn *current_conn;
static bool notify_adc_enabled;
static bool notify_sensor_enabled;
static bool mtu_exchanged;          /* NEW — gate for notifications */

static void adc_ccc_changed(const struct bt_gatt_attr *attr, uint16_t value)
{
    notify_adc_enabled = (value == BT_GATT_CCC_NOTIFY);
    LOG_INF("ADC notify %s", notify_adc_enabled ? "ON" : "OFF");
}

static void sensor_ccc_changed(const struct bt_gatt_attr *attr, uint16_t value)
{
    notify_sensor_enabled = (value == BT_GATT_CCC_NOTIFY);
    LOG_INF("Sensor notify %s", notify_sensor_enabled ? "ON" : "OFF");
}

static uint8_t sensor_cache[12];

static ssize_t read_adc(struct bt_conn *conn, const struct bt_gatt_attr *attr,
                        void *buf, uint16_t len, uint16_t offset)
{
    return bt_gatt_attr_read(conn, attr, buf, len, offset,
                             adc_buf, sizeof(adc_buf));
}

static ssize_t read_sensor(struct bt_conn *conn, const struct bt_gatt_attr *attr,
                           void *buf, uint16_t len, uint16_t offset)
{
    return bt_gatt_attr_read(conn, attr, buf, len, offset,
                             sensor_cache, sizeof(sensor_cache));
}

BT_GATT_SERVICE_DEFINE(wyatt_svc,
    BT_GATT_PRIMARY_SERVICE(SERVICE_UUID),
    BT_GATT_CHARACTERISTIC(ADC_CHAR_UUID,
                           BT_GATT_CHRC_READ | BT_GATT_CHRC_NOTIFY,
                           BT_GATT_PERM_READ,
                           read_adc, NULL, adc_buf),
    BT_GATT_CCC(adc_ccc_changed, BT_GATT_PERM_READ | BT_GATT_PERM_WRITE),
    BT_GATT_CHARACTERISTIC(SENSOR_CHAR_UUID,
                           BT_GATT_CHRC_READ | BT_GATT_CHRC_NOTIFY,
                           BT_GATT_PERM_READ,
                           read_sensor, NULL, sensor_cache),
    BT_GATT_CCC(sensor_ccc_changed, BT_GATT_PERM_READ | BT_GATT_PERM_WRITE),
);

static const struct bt_gatt_attr *adc_attr(void)   { return &wyatt_svc.attrs[1]; }
static const struct bt_gatt_attr *sensor_attr(void) { return &wyatt_svc.attrs[4]; }

/* MTU exchange callback — set flag when done */
static void mtu_exchange_cb(struct bt_conn *conn, uint8_t err,
                             struct bt_gatt_exchange_params *params)
{
    if (err) {
        LOG_WRN("MTU exchange failed: %u", err);
    } else {
        LOG_INF("MTU exchanged: %u", bt_gatt_get_mtu(conn));
        mtu_exchanged = true;
    }
}

static struct bt_gatt_exchange_params mtu_params = {
    .func = mtu_exchange_cb,
};

static void connected(struct bt_conn *conn, uint8_t err)
{
    if (err) { LOG_ERR("Connect failed: %u", err); return; }
    current_conn  = bt_conn_ref(conn);
    mtu_exchanged = false;
    LOG_INF("Connected — requesting MTU exchange");
    /* Initiate MTU exchange from peripheral side */
    bt_gatt_exchange_mtu(conn, &mtu_params);
}

static void disconnected(struct bt_conn *conn, uint8_t reason)
{
    LOG_INF("Disconnected (reason %u)", reason);
    if (current_conn) {
        bt_conn_unref(current_conn);
        current_conn = NULL;
    }
    notify_adc_enabled    = false;
    notify_sensor_enabled = false;
    mtu_exchanged         = false;
    bt_le_adv_start(BT_LE_ADV_CONN_FAST_1, NULL, 0, NULL, 0);
}

BT_CONN_CB_DEFINE(conn_callbacks) = {
    .connected    = connected,
    .disconnected = disconnected,
};

static const struct bt_data ad[] = {
    BT_DATA_BYTES(BT_DATA_FLAGS, BT_LE_AD_GENERAL | BT_LE_AD_NO_BREDR),
    BT_DATA_BYTES(BT_DATA_NAME_COMPLETE,
                  'W','Y','A','T','T',' ','n','R','F','5','4'),
};

/* ── Threads ── */
#define ADC_THREAD_STACK    1024
#define ADC_THREAD_PRIO        5
#define SENSOR_THREAD_STACK 1024
#define SENSOR_THREAD_PRIO     6

K_THREAD_STACK_DEFINE(adc_stack,    ADC_THREAD_STACK);
K_THREAD_STACK_DEFINE(sensor_stack, SENSOR_THREAD_STACK);
static struct k_thread adc_thread_data;
static struct k_thread sensor_thread_data;

static void adc_thread(void *p1, void *p2, void *p3)
{
    ARG_UNUSED(p1); ARG_UNUSED(p2); ARG_UNUSED(p3);

    int ret;
    ret = adc_channel_setup_dt(&adc_ch1);
    if (ret) { LOG_ERR("ADC ch1 setup: %d", ret); return; }
    ret = adc_channel_setup_dt(&adc_ch2);
    if (ret) { LOG_ERR("ADC ch2 setup: %d", ret); return; }

    adc_sequence_init_dt(&adc_ch1, &seq_ch1);
    adc_sequence_init_dt(&adc_ch2, &seq_ch2);

    int64_t next_us = k_ticks_to_us_floor64(k_uptime_ticks());

    while (1) {
        /* 1 ms period → 1 kHz per channel. At BATCH_SIZE=8 that is
         * 125 notifications/sec — comfortable inside a ~15 ms BLE
         * connection interval. */
        next_us += 1000;
        int64_t now_us   = k_ticks_to_us_floor64(k_uptime_ticks());
        int64_t sleep_us = next_us - now_us;
        if (sleep_us > 0) k_busy_wait((uint32_t)sleep_us);

        /* Only sample when connected AND MTU is ready */
        if (!current_conn || !mtu_exchanged) {
            sample_count = 0;
            continue;
        }

        if (sample_count == 0) {
            batch_start_us =
                (uint32_t)k_ticks_to_us_floor64(k_uptime_ticks());
        }

        adc_read_dt(&adc_ch1, &seq_ch1);
        adc_read_dt(&adc_ch2, &seq_ch2);

        uint16_t v1 = (uint16_t)raw_ch1;
        uint16_t v2 = (uint16_t)raw_ch2;

        int off = 4 + sample_count * 4;
        adc_buf[off]     =  v1       & 0xFF;
        adc_buf[off + 1] = (v1 >> 8) & 0xFF;
        adc_buf[off + 2] =  v2       & 0xFF;
        adc_buf[off + 3] = (v2 >> 8) & 0xFF;
        sample_count++;

        if (sample_count >= BATCH_SIZE) {
            adc_buf[0] =  batch_start_us        & 0xFF;
            adc_buf[1] = (batch_start_us >>  8) & 0xFF;
            adc_buf[2] = (batch_start_us >> 16) & 0xFF;
            adc_buf[3] = (batch_start_us >> 24) & 0xFF;

            if (notify_adc_enabled) {
                bt_gatt_notify(current_conn, adc_attr(),
                               adc_buf, sizeof(adc_buf));
            }
            sample_count = 0;
        }
    }
}

static void sensor_thread(void *p1, void *p2, void *p3)
{
    ARG_UNUSED(p1); ARG_UNUSED(p2); ARG_UNUSED(p3);

    LOG_INF("Using nRF54L15 on-die TEMP sensor");
    const float hum_pct = 0.0f;  /* placeholder — no humidity source */

    while (1) {
        k_msleep(2000);
        if (!current_conn || !mtu_exchanged) continue;

        float temp_c = 0.0f;
        if (!nrf_temp_read(&temp_c)) {
            LOG_WRN("TEMP read timed out");
            continue;
        }

        uint32_t ts =
            (uint32_t)k_ticks_to_us_floor64(k_uptime_ticks());
        sensor_cache[0] =  ts        & 0xFF;
        sensor_cache[1] = (ts >>  8) & 0xFF;
        sensor_cache[2] = (ts >> 16) & 0xFF;
        sensor_cache[3] = (ts >> 24) & 0xFF;
        memcpy(&sensor_cache[4], &temp_c,  4);
        memcpy(&sensor_cache[8], &hum_pct, 4);

        if (notify_sensor_enabled) {
            bt_gatt_notify(current_conn, sensor_attr(),
                           sensor_cache, sizeof(sensor_cache));
        }
        LOG_INF("Die temp: %.2f C", (double)temp_c);
    }
}

int main(void)
{
    int err;
    LOG_INF("WYATT nRF54L15 booting");

    err = bt_enable(NULL);
    if (err) { LOG_ERR("bt_enable: %d", err); return err; }

    err = bt_le_adv_start(BT_LE_ADV_CONN_FAST_1,
                          ad, ARRAY_SIZE(ad), NULL, 0);
    if (err) { LOG_ERR("adv_start: %d", err); return err; }
    LOG_INF("Advertising as 'WYATT nRF54'");

    k_thread_create(&adc_thread_data, adc_stack,
                    K_THREAD_STACK_SIZEOF(adc_stack),
                    adc_thread, NULL, NULL, NULL,
                    ADC_THREAD_PRIO, 0, K_NO_WAIT);
    k_thread_name_set(&adc_thread_data, "adc_sampler");

    k_thread_create(&sensor_thread_data, sensor_stack,
                    K_THREAD_STACK_SIZEOF(sensor_stack),
                    sensor_thread, NULL, NULL, NULL,
                    SENSOR_THREAD_PRIO, 0, K_NO_WAIT);
    k_thread_name_set(&sensor_thread_data, "temp_reader");

    return 0;
}