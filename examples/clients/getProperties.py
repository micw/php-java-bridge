#!/usr/bin/python

# PHP/Java Bridge test client written in python.
# Run this example with: python getProperties.py

import socket
import sys

HOST = 'localhost'    # The host running the server part of the bridge
PORT = 9267           # The standard port of the bridge
s = None
for res in socket.getaddrinfo(HOST, PORT, 
socket.AF_UNSPEC, socket.SOCK_STREAM):
    af, socktype, proto, canonname, sa = res
    try:
	s = socket.socket(af, socktype, proto)
    except socket.error, msg:
	s = None
	continue
    try:
	s.connect(sa)
    except socket.error, msg:
	s.close()
	s = None
	continue
    break
if s is None:
    print 'Could not connect to socket.'
    print 'Please start the bridge, for example with java -jar JavaBridge.jar INET:9267 5 ""'
    sys.exit(1)

# ask for System.getProperties()
s.send('<C value="java.lang.System" p="Class" id="0"></C>')
data = s.recv(1024); # System: object=1
s.send('<I value="1" method="getProperties" p="Invoke" id="0"></I>');
data = s.recv(1024); # Properties: object=2
# create a map for the properties object ...
s.send('<I value="0" method="getPhpMap" p="Invoke" id="0"> <Object value="2"/> </I>')
data = s.recv(1024); # PhpMap: object=3
# ... to iterate over the values ...
s.send('<I value="3" method="hasMore" p="Invoke" id="0"></I>');
data = s.recv(1024); # yes
s.send('<I value="3" method="currentKey" p="Invoke" id="0"></I>');
key = s.recv(1024);  # string: key
# ... show only the first entry.
s.send('<I value="3" method="currentData" p="Invoke" id="0"></I>');
data = s.recv(1024); # value
s.close()
# should have received the first entry from java.lang.System.getProperties()
print 'Received:\n', `data`
