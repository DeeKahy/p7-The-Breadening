start java -cp dist/smartlamp.jar com.uppaal.smartlamp.Main -C localhost 6521 -M 0
..\tron -Q 6521 -P 10,100 -F 300 -I SocketAdapter -v 9 LightContr.xml -- localhost 9999
@pause
