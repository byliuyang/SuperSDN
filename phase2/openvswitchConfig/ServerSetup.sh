#/bin/sh!

echo  '¯\_(ツ)_/¯'
sed -i -e 's/4404/4516/g' /etc/apt/sources.list
sed -i -e 's/trusty/xenial/g' /etc/apt/sources.list
apt update

if [ $1 = "2" ]; then
	echo "DEPLOYING SERVER 2"
	echo '# This file describes the network interfaces available on your system
	# and how to activate them. For more information, see interfaces(5).

	# The loopback network interface
	auto lo
	iface lo inet loopback

	auto eth0
	iface eth0 inet dhcp

	auto eth0:0
	iface eth0:0 inet static
	address 10.45.2.34
	netmask 255.255.255.224

	auto eth0:1
	iface eth0:1 inet static
	address 10.45.2.65
	netmask 255.255.255.224

	auto eth0:2
	iface eth0:2 inet static
	address 10.45.2.97
	netmask 255.255.255.224' > /etc/network/interfaces
	/etc/init.d/networking restart
	apt install -y openvswitch-switch
	sysctl -w net.ipv4.ip_forward=1
	service openvswitch-switch start
	ovs-vsctl add-br br0 && ovs-vsctl add-port br0 eth0 &&  ifconfig eth0 0 && dhclient -r eth0 &&  dhclient br0 && ifconfig br0 10.45.2.2 &&  ovs-vsctl set-controller br0 tcp:10.45.2.4:6653
else
	echo "ARGUEMENT NOT RECOGNIZED BUT DEPLOYED COMMON SETTINGS"
fi

