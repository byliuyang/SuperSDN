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
function testConn(address, count, max, total){
  // Create a client and start a timer.
  var client = new net.Socket();
  var start = new Date();

  // Connect the client
  client.connect(80, address, function() {
    // Once connected end the timer.
    var end = new Date();
    // BRUTALLY DESTROY THE CLIENT!!!
    client.destroy();
    // Calculate elapsed time
    var elapsed = end.getTime() - start.getTime() ;
    // Log the result
    console.log(count + ": Connected within: " + elapsed + " ms");
    // Count until max is reached by count
    if(count < max){
      // Recurse with count + 1
      testConn(address, count+1, max, total+elapsed);
    } else {
      // Ended so give avarage
      console.log("Avg time: " + total/max + " ms");
    }
  });

}

rl.question('Please enter the server address: ', (answer) => {
  const options = {
    family: 4,
    hints: dns.ADDRCONFIG | dns.V4MAPPED,
  };

  // Resolve DNS
  dns.lookup(answer, options, (err, address, family) => {
    console.log("Resolved adress is: " + address);
    console.log("Testing:");
    testConn(address, 0, 10, 0);
  })

  rl.close();
});
