#include <Wire.h>
#include <SparkFun_VL53L1X.h>

SFEVL53L1X sensor(Wire);

const int LED_PIN = 13;

const bool testMode = false;

const int minimumDetectionDistanceClose = 4;
const int minimumDetectionDistanceField = 50;

int minimumDetectionDistance = minimumDetectionDistanceClose; // (default) mm 

int baselineMm = 0;
int detectionCount = 0;
int totalDetectionCount = 0;


// can only reliably detect species bigger than:  (0-6 - 0.7)  cm at close range
// can only reliably detect species bigger than:  5 cm at field range

// - - setup - - 

void setup() {

  Serial.begin(115200);
  delay(3000);              // 3 seconds
  Serial.println("-");
  Serial.println("- - boot - -");

  // I2C for XIAO
  Wire.begin(4, 5);           // SDA, SCL
  Wire.setClock(100000);      // stable
  delay(100);

  // Sensor init
  if ( sensor.begin() != 0 ) {
    Serial.println("sensor.begin() != 0:  - - failed - -");
    while (1);
  }

  Serial.println("sensor ok");

  sensor.setDistanceModeShort();   
  sensor.setTimingBudgetInMs(200);
  delay(5);
  sensor.setIntermeasurementPeriod(250);
  delay(5);

  sensor.startRanging();
  Serial.println("ranging started");

  delay(200);

  pinMode(LED_PIN, OUTPUT);
  digitalWrite(LED_PIN, HIGH);

  setBaseline(15);
  
  Serial.println("READY");

}


// - - set baseline - - 

void setBaseline( int sampleCount ) {  // average of n samples

  Serial.println("baseline calibration: started");
  Serial.println("timeout: 10 seconds");

  delay(100);

  long sum = 0;
  int sampleCountActual = 0;

  const unsigned long timeoutMs = 10000;
  unsigned long startTime = millis();

  while ( sampleCountActual < sampleCount ) { 

    if ( sensor.checkForDataReady() ) {
      int distanceReading = sensor.getDistance();
      sensor.clearInterrupt();
      if ( distanceReading > 0 ) {
        sum += distanceReading; 
        sampleCountActual ++;
      }
    }

    if ( (millis() - startTime) > timeoutMs ) {
      Serial.println("baseline: timedout");
      Serial.print("sampleCountActuals: "); Serial.println( sampleCountActual );
      break;
    }

    delay(5);

  }

  if (sampleCountActual == 0) {
    Serial.println("baseline calibration  - - failed - - ");
    Serial.println("sampleCountActual: 0");
    baselineMm = baselineMm; // (default)/ current mm;
  }
  else {
    baselineMm = (int) ( sum / sampleCountActual );
    Serial.print("sample count actual: "); Serial.println( sampleCountActual );
    Serial.print("new baseline (mm): "); Serial.println( baselineMm );
  }

}



// - - loop - - 



void loop() {


  // - - read usb - - 

  if ( Serial.available() ) {

    String cmd = Serial.readStringUntil('\n');
    cmd.trim();

    if ( cmd == "CLOSE" ) { 

      sensor.stopRanging();
      delay(100);
      sensor.setDistanceModeShort();  
      delay(5);
      sensor.setTimingBudgetInMs(200);
      delay(5);
      sensor.setIntermeasurementPeriod(250);
      delay(5);
      minimumDetectionDistance = minimumDetectionDistanceClose; 
      Serial.println("mode:close");
      delay(5);
      sensor.startRanging();
      Serial.println("ranging started");

      setBaseline(15);
      detectionCount = 0;
      
      Serial.println("READY");

    }
    else if ( cmd == "FIELD" ) { 

      sensor.stopRanging();
      delay(100);
      sensor.setDistanceModeLong(); 
      delay(5);
      sensor.setTimingBudgetInMs(100);
      delay(5);
      sensor.setIntermeasurementPeriod(120);
      delay(5);
      minimumDetectionDistance = minimumDetectionDistanceField;
      Serial.println("mode:field");
      delay(5);
      sensor.startRanging();
      Serial.println("ranging started");

      setBaseline(15);
      detectionCount = 0;

      Serial.println("READY");

    }
    else if ( cmd == "CAL" ) {
      setBaseline(15);
      detectionCount = 0;
      Serial.println("READY");

    }

  }


  // - - read sensor - - 

  if ( !sensor.checkForDataReady() ) {
    delay(5);
    return;
  }

  int distanceReading = sensor.getDistance();  // mm
  sensor.clearInterrupt();

  if ( testMode ) {
    Serial.print("distanceReading (mm): "); Serial.println(distanceReading);
  }

  if ( distanceReading <= 0 ) {
    Serial.println(" - - distanceReading was <= 0 - - ");
    Serial.print("distanceReading (mm): "); Serial.println(distanceReading);
    delay(100);
    return; 
  }

  int distanceFromBaseLine = abs( distanceReading - baselineMm );

  if ( testMode ) {
    Serial.print("distanceFromBaseLine (mm): "); Serial.println( distanceFromBaseLine );
  }

  if ( distanceFromBaseLine >= minimumDetectionDistance ) detectionCount ++;
  else detectionCount = 0;

  if ( testMode ) {
    Serial.print("detection count: "); Serial.println(detectionCount);
  }
  
  if ( detectionCount >= 2 ) {

    Serial.println("DETECTION");
    totalDetectionCount ++;
    
    if ( testMode) {
      Serial.print("total detection count: "); Serial.println(totalDetectionCount);
      Serial.print("distanceReading (mm): "); Serial.println(distanceReading);
      Serial.print("distanceFromBaseLine (mm): "); Serial.println( distanceFromBaseLine );
    }
    
    digitalWrite(LED_PIN, LOW);
    delay(2000);      // cooldown 2 seconds  // - - blocks reading usb - -
    digitalWrite(LED_PIN, HIGH);

    detectionCount = 0;

  } else {
    delay(5); 
  }

}
