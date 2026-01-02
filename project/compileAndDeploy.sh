# cd wildlife;
set -e  # stops if error
./gradlew compileDebugKotlin 
# ./gradlew assembleDebug --offline --daemon
./gradlew assembleDebug
echo ''
echo '- - installing - -'
echo ''
adb install -r app/build/outputs/apk/debug/app-debug.apk
echo ''
echo '- - - - - -'
echo ''

