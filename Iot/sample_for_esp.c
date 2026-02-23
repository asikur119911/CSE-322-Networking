#include <WiFi.h>
#include <PubSubClient.h>

// Wi-Fi credentials
const char* ssid     = "Wokwi-GUEST"; // Wokwi default guest Wi-Fi
const char* password = "asik";

// MQTT broker (public broker example)
const char* mqtt_server = "broker.hivemq.com";

WiFiClient espClient;
PubSubClient client(espClient);

const int ledPin = 2; // Onboard LED pin (ESP32)

void callback(char* topic, byte* payload, unsigned int length) {
  // Convert payload to string
  String message;
  for (unsigned int i = 0; i < length; i++) {
    message += (char)payload[i];
  }

  Serial.print("Received [");
  Serial.print(topic);
  Serial.print("] : ");
  Serial.println(message);
  
  // TODO: Put appropriate messages inside the conditions
  if (message == "ON") {
    digitalWrite(ledPin, HIGH);  // Turn LED ON
  } else if (message == "OFF") {
    digitalWrite(ledPin, LOW);   // Turn LED OFF
  }
}

void reconnect() {
  while (!client.connected()) {
    Serial.print("Connecting to MQTT…");
    const char* topic = "buet/cse/2105144/led"; // TODO: The topic will be "led" under your id under "cse" under "buet"
    if (client.connect("ESP32_Wokwi_LED")) {
      Serial.println("connected");
      client.subscribe(topic);
    } else {
      Serial.print("failed, rc=");
      Serial.print(client.state());
      Serial.println(" try again in 5s");
      delay(5000);
    }
  }
}

void setup() {
  pinMode(ledPin, OUTPUT);
  digitalWrite(ledPin, LOW);

  Serial.begin(115200);

  // Connect Wi-Fi
  WiFi.begin(ssid, password);
  Serial.print("Connecting to Wi-Fi…");
  while (WiFi.status() != WL_CONNECTED) {
    delay(500);
    Serial.print(".");
  }
  Serial.println("Wi-Fi connected!");

  // MQTT client
  client.setServer(mqtt_server, 1883);
  client.setCallback(callback);
}

void loop() {
  if (!client.connected()) {
    reconnect();
  }
  client.loop();
}
