#Assignment 3
v1.1
Team Members: Lucas Lu, Yexuan Gao
Programming Language Used: Java 11
IDE: VS Code
Membership Tracking Methods Implemented:
FD, T

Please only test this programas Docker containers. Run 2+ servers locally will not work.

How to run the project:

- Start servers with FD as Docker containers:
  1. Go to "./docker_server/" directory
  2. Make sure "nodes.cfg" is under "./tmp/"
  3. "./tmp/nodes.cfg" should contain ip address and port in the format of "ip_address:port" for all the servers participating (see provided). "ip_address" should be the docker container ip address on the host machine. Provided file has ip addresses used in performance testing.
  4. Uncomment the command for "#TCP Server - DF", comment out all other lines
  5. Same as A2, build docker image in "docker_server/" then run as containers, the ip address addresses of these containers should be included in the "./tmp/nodes.cfg" 
  6. To test the server, go to "./target/" directory, run client command same as A2.
  7. To increase the number of participating nodes, either change the "nodes.cfg" inside each container; or rebuild images with modified "docker_server/tmp/nodes.cfg". 1-container, 3-container, 5-container performances have been tested.
   


- Start servers with T as Docker containers:
  1. Go to "./docker_server/", edit "runserver.sh" uncomment lines for "TCP Server - centralized node directory", comment out all other lines. Then build image and run as a container.
  2. Get the membership server container id.
     Use "sudo docker exec -it <membership server container> bash" to go in the membership container and use "ifconfig" to get its ip address. Copy it for the next step.
  3. Under "docker_server/", in "runserver.sh" uncomment the line for "#TCP Server - T" and comment out all other lines, edit the membership server container ip address at the end of "java -jar GenericNode.jar ts 1234" if not "172.17.0.2".
  4. Then build image and run as containers.
  5. To test the server, go to "./target/" directory, run client command same as A2.