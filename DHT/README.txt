To generate a SuperNode:
java -cp bin -Djava.security.policy=src/policyfile SuperNode [maxNumNodes]

To generate a ClientNode:
java -cp bin -Djava.security.policy=src/policyfile ClientNode [SuperNode's IP Address] [maxNumNodes]

To generate a NodeDHT:
java -cp bin -Djava.security.policy=src/policyfile NodeDHT [Port Number] [SuperNode's IP Address] [maxNumNodes]