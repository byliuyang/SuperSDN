#/bin/sh!

if [ "$#" -ne 2 ]; then
    echo "Usage: sudo ./setup [Server Number] [Admin IP]"
    exit
fi

echo  '¯\_(ツ)_/¯'
sed -i -e 's/4404/4516/g' /etc/apt/sources.list
sed -i -e 's/trusty/xenial/g' /etc/apt/sources.list
apt update

if [ $1 = "1" ]; then
  echo "DEPLOYING SERVER 1"
  echo 'dns-nameserver 10.45.2.2' >> /etc/network/interfaces
  /etc/init.d/networking restart
  apt install -y openvswitch-switch
  service openvswitch-switch start
  ovs-vsctl add-br br0 && ovs-vsctl add-port br0 eth0 &&  ifconfig eth0 0 && dhclient -r eth0 &&  dhclient br0 && ifconfig br0 10.45.2.1 &&  ovs-vsctl set-controller br0 tcp:$2:6653
  for i in `seq 48 62`;
  do
    ifconfig br0:$i 10.45.2.$i up
  done
elif [ $1 = "2" ]; then
	echo "DEPLOYING SERVER 2"
	apt install -y bind9
	echo "zone \"team2.4516.cs.wpi.edu\" {
	    type master;
	    file \"/etc/bind/zones/db.team2.4516.cs.wpi.edu\"; # zone file path
	};" > /etc/bind/named.conf.local
	mkdir /etc/bind/zones
	cp /etc/bind/db.local /etc/bind/zones/db.team2.4516.cs.wpi.edu
echo ";
; BIND data file for local loopback interface
;
\$TTL	60
@	IN	SOA	localhost. root.localhost. (
2		; Serial
604800		; Refresh
86400		; Retry
2419200		; Expire
604800 )	; Negative Cache TTL
;
;@	IN	NS	localhost.
;@	IN	A	127.0.0.1
;@	IN	AAAA	::1
; name servers - NS records
	IN      NS      ns1.team2.4516.cs.wpi.edu.

; name servers - A records
ns1.team2.4516.cs.wpi.edu.                     IN      A      10.45.2.2

; A records
www.team2.4516.cs.wpi.edu.                     IN      A      10.45.1.10
" > /etc/bind/zones/db.team2.4516.cs.wpi.edu

	service bind9 start
	chmod 777 /etc/bind/zones/db.team2.4516.cs.wpi.edu
	named-checkzone team2.4516.cs.wpi.edu /etc/bind/zones/db.team2.4516.cs.wpi.edu
	apt install -y openvswitch-switch
	service openvswitch-switch start
	ovs-vsctl add-br br0 && ovs-vsctl add-port br0 eth0 &&  ifconfig eth0 0 && dhclient -r eth0 &&  dhclient br0 && ifconfig br0 10.45.2.2 &&  ovs-vsctl set-controller br0 tcp:$2:6653
elif [ $1 = "3" ]; then
	echo "DEPLOYING SERVER 3"
	apt install -y bind9
	apt install -y apache2
	chmod 777 /var/www/html/index.html
	echo "<!Doctype html>
	<html><head>
	<title>Real Server</title>
	</head>
	<body cz-shortcut-listen=\"true\">
	<h1>This is the <strong style=\"color:green\">Real Server</strong></h1>" > /var/www/html/index.html

	apt install -y openvswitch-switch
	service openvswitch-switch start
	ovs-vsctl add-br br0 && ovs-vsctl add-port br0 eth0 &&  ifconfig eth0 0 && dhclient -r eth0 &&  dhclient br0 && ifconfig br0 10.45.2.3 &&  ovs-vsctl set-controller br0 tcp:$2:6653

    for i in `seq 128 254`;
	do
		ifconfig br0:$i 10.45.2.$i up
	done
else
	echo "ARGUEMENT NOT RECOGNIZED BUT DEPLOYED COMMON SETTINGS"
fi