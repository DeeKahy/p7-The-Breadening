#!/bin/sh
exec ../tron -qQ 6521 -P 10,100 -F 300 -I SocketAdapter -D light.log -v 0 LightContr.xml -- localhost 9999
