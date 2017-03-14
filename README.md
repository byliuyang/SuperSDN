# SuperSDN

DNS Capabilities via Software-Defined Networking

## Getting Started

These instructions will get you a copy of the project up and running on your local machine for development and testing purposes. See deployment for notes on how to deploy the project on a live system.

### Prerequisites

What you need to have before installing SuperSDN

 - 4 Ubuntu 16.04 virtual machines conncted to the same network

 	* Host 1 for the client
 	* Host 2 for the Gateway
  	* Host 3 for the Real Web server
  	* Host 4 for the machine that runs the OpenFlow controller, DNS server, and Honeypot DNS server.

### Installing

Configure IP addresses for virtual machines

  - Host 1: 10.45.x.1
  - Host 2: 10.45.x.2
  - Host 3: 10.45.x.3
  - Host 4: 10.45.x.4

```
Give the example
```
Add IP aliases to each of these machines with CIDR prefix length of 27. 

| Host # | Original Address | Additional Alias(es)	 | Additional Static Routes                        |
| :-------: | :----------------------: | :--------------------------: | :----------------------------------------------------: || 1 	      | 10.45.x.1               | 10.45.x33/27             | 10.45.x128/25 via gateway 10.45.x.34 |
| 2  	      | 10.45.x.2		| 10.45.x.34/27 <br/>10.45.x.65/27 <br/> 10.45.x.97/27 |      || 3          | 10.45.x.3		| 10.45.x.66/27           | 10.45.x.0/25 via gateway 10.45.x.65     |
| 4 	      | 10.45.x.4 		| 10.45.x.98/27	      |	                                                                      |

Install apache on Host 3 and Host 4

```
Give the example
```
Edit index.html on Host 4 to indicate it's Honeypot

```
Give the example
```

Install bind9 on Host 4

Add a entry to DNS zone file for the web server

```
www.team[x].4516.cs.wpi.edu. A 10.45.x.10
```

Install Open vSwitch on Host 3

```
Give the example
```

Install Floodlight on Host 4

```
Give the example
```

End with an example of getting some data out of the system or using it for a little demo

## Built With

* [Apache](https://httpd.apache.org/docs/2.4) - The web server
* [Bind9](http://www.bind9.net) - The DNS server
* [Open vSwitch](http://openvswitch.org) - The virtual switch
* [Floodlight](http://www.projectfloodlight.org) - The OpenFlow controller

## Contributing

Please read [CONTRIBUTING.md](https://gist.github.com/PurpleBooth/b24679402957c63ec426) for details on our code of conduct, and the process for submitting pull requests to us.

## Versioning

We use [SemVer](http://semver.org/) for versioning. For the versions available, see the [tags on this repository](https://github.com/byliuyang/SuperSDN/tags). 

## Authors

* **Harry Liu** - *Initial work* - [byliuyang](https://github.com/byliuyang)
* **Can Alper** - *Initial work* - [calper95](https://github.com/calper95)

See also the list of [contributors](https://github.com/byliuyang/SuperSDN/contributors) who participated in this project.

## License

This project is licensed under the MIT License - see the [LICENSE.md](LICENSE.md) file for details

## Acknowledgments

* Project idea borrowed from Professor Craig Shue@WPI
