net = require('net');
url = require('url');
dns = require('dns');
http = require('http');
request = require('request');

var readline = require('readline');
var rl = readline.createInterface({
    input: process.stdin,
    output: process.stdout,
    terminal: false
});

rl.question('Please enter the server address: ', (answer) => {
    // TODO: Log the answer in a database

		request(answer, function (error, response, body) {
		  console.log('error:', error); // Print the error if one occurred
		  console.log('statusCode:', response && response.statusCode); // Print the response status code if a response was received
		  console.log('body:', body); // Print the HTML for the Google homepage.
		});

    rl.close();
});
