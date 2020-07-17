# Raspberry

How to set a new raspberry

### Set protocol and other basic stuff
````
sudo nano /etc/bluetooth/main.conf
# set these variables
DiscoverableTimeout = 0
FastConnectable = true
AutoEnable=true
# add line under [general]
Enable=Source,Sink,Media,Socket
````

### Set auto discoverable to always on and auto accept pairing
````
sudo nano /home/pi/.bashrc
# add these commands to the end of the file
sudo hciconfig hci0 piscan 
sudo hciconfig hci0 sspmode 1
````
Restart the raspberry to get the changes to work immediately

### Change bluetooth discoverable name
````
sudo nano /etc/machine-info
# add this property to the file
PRETTY_HOSTNAME=Carebnb Device
Service Bluetooth restart
````

### Install python dependencies
````
# install wifi depedency
sudo pip install wifi
# install bluetooth dependency
sudo apt-get install python-bluez
# edit file
sudo nano /etc/systemd/system/dbus-org.bluez.service
# add "-C" to this line
ExecStart=/usr/lib/bluetooth/bluetoothd -C
# Load serial port profile
sudo sdptool add SP
````
Restart the raspberry to get the changes to work immediately

### Register script
````
chmod +x setWifi.py
sudo nano /etc/rc.local
# add this line at the end before exit 0
(sleep 10;sudo /path/to/script/setWifi.py)&
````