start java -cp dist/smartlamp.jar com.uppaal.smartlamp.Main -M 0
..\tron -P 10,100 -F 300 -I SocketAdapter -v 9 LightContr.xml -- localhost 9999
@pause
