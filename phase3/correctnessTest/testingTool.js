// REQUIRED LIBS
net = require('net');
dns = require('dns');

// Readline module
var readline = require('readline');
var rl = readline.createInterface({
  input: process.stdin,
  output: process.stdout,
  terminal: false
});


// Recursive async function to test network connect time
function testConn(dnsstart, answer, address, count, max, total){
  // Create a client and start a timer.
  var client = new net.Socket();
  var start = new Date();

  client.on("error", function(error){
      console.log("error", error);
  });

  console.log("Tring to connect...");

  // Connect the client
  client.connect(80, address, function() {
    if(count < max){
      // Once connected end the timer.
      var end = new Date();
      // BRUTALLY DESTROY THE CLIENT!!!
      client.destroy();
      // Calculate elapsed time
      var elapsed = end.getTime() - start.getTime() ;
      // Log the result
      console.log(count + ": Connected within: " + elapsed + " ms");

      setTimeout(function() {
        // Count until max is reached by count

        console.log("TTL expired");
        var timeOut = setTimeout(function() {
          client.destroy();
          console.log("Cannot connect to server ");
          const options = {
            family: 4,
            hints: dns.ADDRCONFIG | dns.V4MAPPED,
          };

          // Resolve DNS
          dns.lookup(answer, options, (err, address, family) => {
            console.log("Resolved adress for www.team2.4516.cs.wpi.edu is: " + address);
            var dnsstart = new Date();
            testConn(dnsstart,answer,address, count + 1, max, total + elapsed);
          })
        }, 3000);
        client.connect(80, address, function() {
          console.log("Connected.")
          clearTimeout(timeOut);
        });
      }, 7000);
      } else {
        // Ended so give avarage
        console.log("Avg time: " + total/max + " ms");
      };
  });
}

  const options = {
    family: 4,
    hints: dns.ADDRCONFIG | dns.V4MAPPED,
  };

  // Resolve DNS
  dns.lookup("www.team2.4516.cs.wpi.edu", options, (err, address, family) => {
    console.log("Resolved adress for www.team2.4516.cs.wpi.edu is: " + address);
    console.log("Testing:");
    var dnsstart = new Date();
    testConn(dnsstart,"www.team2.4516.cs.wpi.edu",address, 0, 100, 0);
  });


