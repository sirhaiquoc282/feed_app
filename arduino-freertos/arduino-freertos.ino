#include <Arduino_FreeRTOS.h>
#include <Servo.h>
#include <semphr.h>

#define SERVO_PIN 9
#define IR_SENSOR_PIN 2
#define TRIG_PIN 3
#define ECHO_PIN 4

Servo myServo;
int distance = 0;
bool isAutoMode = true;
unsigned long lastServoTime = 0;
const int waitTime = 15000;

void TaskControl(void *pvParameters);
void TaskSensor(void *pvParameters);

void setup() {
  Serial.begin(9600);
  
  myServo.attach(SERVO_PIN);
  myServo.write(0);
  
  pinMode(IR_SENSOR_PIN, INPUT);
  pinMode(TRIG_PIN, OUTPUT);
  pinMode(ECHO_PIN, INPUT);
  
  xTaskCreate(TaskControl, "Control", 128, NULL, 2, NULL);
  xTaskCreate(TaskSensor, "Sensor", 128, NULL, 1, NULL);
}

void loop() {}

void TaskControl(void *pvParameters) {
  (void) pvParameters;

  for (;;) {
    if (Serial.available()) {
      String cmd = Serial.readStringUntil('\n');
      cmd.trim();

      if (cmd == "DO_FED") {
        openServo();
      } else if (cmd == "AUTO_ON") {
        isAutoMode = true;
        Serial.println("MODE:AUTO_ON");
      } else if (cmd == "AUTO_OFF") {
        isAutoMode = false;
        Serial.println("MODE:AUTO_OFF");
      }
    }

    if (isAutoMode && digitalRead(IR_SENSOR_PIN) == LOW) {
      if (millis() - lastServoTime > waitTime) {
        openServo();
      }
    }

    vTaskDelay(100 / portTICK_PERIOD_MS);
  }
}

void TaskSensor(void *pvParameters) {
  (void) pvParameters;

  for (;;) {
    digitalWrite(TRIG_PIN, LOW); delayMicroseconds(2);
    digitalWrite(TRIG_PIN, HIGH); delayMicroseconds(10);
    digitalWrite(TRIG_PIN, LOW);
    
    long duration = pulseIn(ECHO_PIN, HIGH);
    distance = duration * 0.034 / 2;

    Serial.print("DIST:"); 
    Serial.println(distance);

    vTaskDelay(5000 / portTICK_PERIOD_MS);
  }
}

void openServo() {
  myServo.write(40);
  vTaskDelay(1000 / portTICK_PERIOD_MS);
  myServo.write(0);
  lastServoTime = millis();
  Serial.println("REPORT:FED_DONE");
}