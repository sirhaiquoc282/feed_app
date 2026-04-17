#include <WiFi.h>
#include <WiFiClientSecure.h>
#include <PubSubClient.h>

// WiFi & MQTT
const char* ssid = "Redmi Note 14";
const char* password = "11111111";
const char* mqtt_server = "p6666028.ala.asia-southeast1.emqxsl.com";

WiFiClientSecure espClient;
PubSubClient client(espClient);

// ===== MQTT TOPIC =====
#define TOPIC_FED "fed"
#define TOPIC_STATUS "status"
#define TOPIC_FOOD_STATUS "food_status"
#define TOPIC_FOOD_CHECK "food_check"
#define TOPIC_AUTO_SENSOR "auto_sensor"

// ===== CALLBACK NHẬN LỆNH TỪ APP =====
void callback(char* topic, byte* payload, unsigned int length) {
  String msg = "";
  for (int i = 0; i < length; i++) msg += (char)payload[i];
  msg.trim();

  String strTopic = String(topic);

  Serial.printf("[MQTT] %s -> %s\n", topic, msg.c_str());

  // ===== 1. CHO ĂN NGAY =====
  if (strTopic == TOPIC_FED && msg == "FED") {
    Serial2.println("DO_FED");
  }

  // ===== 2. CHECK THỨC ĂN =====
  else if (strTopic == TOPIC_FOOD_CHECK && msg == "CHECK") {
    Serial2.println("MEASURE");
  }

  // ===== 3. AUTO SENSOR =====
  else if (strTopic == TOPIC_AUTO_SENSOR) {
    if (msg == "ON") {
      Serial2.println("AUTO_ON");
      client.publish(TOPIC_STATUS, "AUTO_ON"); //  phản hồi cho app
    } else if (msg == "OFF") {
      Serial2.println("AUTO_OFF");
      client.publish(TOPIC_STATUS, "AUTO_OFF"); //  phản hồi cho app
    }
  }
}

void setup() {
  Serial.begin(115200);
  Serial2.begin(9600, SERIAL_8N1, 16, 17);

  WiFi.begin(ssid, password);
  while (WiFi.status() != WL_CONNECTED) {
    delay(500);
    Serial.print(".");
  }

  Serial.println("\nWiFi Connected");

  espClient.setInsecure();
  client.setServer(mqtt_server, 8883);
  client.setCallback(callback);
}

void loop() {
  if (!client.connected()) reconnect();
  client.loop();

  // ===== NHẬN DỮ LIỆU TỪ ARDUINO =====
  if (Serial2.available()) {
    String data = Serial2.readStringUntil('\n');
    data.trim();

    if (data.length() > 0) {

      // ===== TRẢ MỨC THỨC ĂN =====
      if (data.startsWith("DIST:")) {
        String distVal = data.substring(5);
        client.publish(TOPIC_FOOD_STATUS, distVal.c_str());
      }

      // ===== XÁC NHẬN ĐÃ CHO ĂN =====
      else if (data == "REPORT:FED_DONE") {
        client.publish(TOPIC_STATUS, "OPEN_FED");
      }

      // ===== TRẠNG THÁI AUTO =====
      else if (data == "MODE:AUTO_ON") {
        client.publish(TOPIC_STATUS, "AUTO_ON");
      }
      else if (data == "MODE:AUTO_OFF") {
        client.publish(TOPIC_STATUS, "AUTO_OFF");
      }
    }
  }
}

// ===== RECONNECT MQTT =====
void reconnect() {
  while (!client.connected()) {
    Serial.print("Connecting MQTT...");

    String clientId = "ESP32_" + String(random(1000, 9999));

    if (client.connect(clientId.c_str(), "freertos", "freertos")) {
      Serial.println("Connected");

      client.subscribe(TOPIC_FED);
      client.subscribe(TOPIC_FOOD_CHECK);
      client.subscribe(TOPIC_AUTO_SENSOR);

    } else {
      Serial.print("Fail, rc=");
      Serial.println(client.state());
      delay(3000);
    }
  }
}