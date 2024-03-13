#Assignment 3
Team Members: Lucas Lu, Yexuan Gao
Programming Language Used: Java 11
IDE: VS Code
Membership Tracking Methods Implemented:
FD, T

How to run the project:
- Start Servers with FD locally:
  1. nodes.cfg should be placed under "GenericNode/tmp/"
  2. nodes.cfg should contain ip address and port in the format of "ip_address:port" for all the servers participating (see provided)
  3. In "/target/", run "$ java -jar GenericNode.jar ts <server port>" to start a server (same as A2). Nothing should follow <sever port> otherwise will be treated as TCP Centralized Membership tracking (T) used. <server port> must not be 4410.
  4. In "/target/:, run "$ java -jar GenericNode.jar tc <server ip> <server port> <cmd> ..." as a client (same as A2)
  5. Servers will periodically read the "/tmp/nodes.cfg" every 10s.
   
- Start servers with FD as Docker containers:
  1. Make sure "nodes.cfg" is under "docker_server/tmp/"
  2. "/tmp/nodes.cfg" should contain ip address and port in the format of "ip_address:port" for all the servers participating (see provided). "ip_address" should be the docker container ip address on the host machine. Provided file has ip addresses used in performance testing.
  3. Same as A2, build docker image in "docker_server/" then run as containers
  4. To increase the number of participating nodes, either change the "nodes.cfg" inside each container; or rebuild images with modified "docker_server/tmp/nodes.cfg". 1-container, 3-container, 5-container performances have been tested.
   
- Start servers with T locally:
  1. In "/target/", run "$ java -jar GenericNode.jar ts 4410" to start the centralized membership server. Port 4410 is dedicated to membership server. No other KV store server should use this port.
  2. After a membership server is running, get the ip address of the membership server.
  3. In "/target/", run "$ java -jar GenericNode.jar ts <server port> <membership ip address>" to start KV store tcp server with centralized membership server method.

- Start servers with T as Docker containers:
  1. Under "docker_server/", edit "runserver.sh" uncomment lines for membership server. Then build image and run as a container.
  2. Use "sudo docker exec -it <membership server container> bash" to go in the membership container and use "ifconfig" to get the ip address.
  3. Under "docker_server/", edit "runserver.sh" uncomment lines for tcp server, append the membership server container ip address at the end of "java -jar GenericNode.jar ts 1234".
  4. Comment out the lines for membership server. Then build image and run as containers.

