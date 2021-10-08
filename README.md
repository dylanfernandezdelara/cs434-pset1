# cs434-pset1

My current directory structure for the server has "/home/httpd/html/zoo/classes/cs434/" set as my default root. 
Whenever the client connects to a host they are given two options that I specified in my config file— cicada.cs.yale.edu 
and mobile.cicada.cs.yale.edu. If the -config flag is set then I update the root of my server directory to be the document root
associated with either server name. For my local directory on my system when I was testing, I had one folder which is hw1 as my
root and inside I had text.txt file and another folder containing other static files. By having two folders I was able to test whether or not I could find the correct file path. I started working on a CGI implementation, but decided to go for a FastCGI
implementation which I will include in the final submission of this pset. I plan on refactoring a lot of the code I wrote
for the multi thread implementation and this checkpoint helped me get started on a single thread implementation. 
