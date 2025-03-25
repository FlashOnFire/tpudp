```.mermaid
sequenceDiagram
    participant Client
    participant Server

    Note right of Client: Initialize DatagramSocket (port: xxxxx)
    Note right of Server: Initialize DatagramSocket (IP: localhost, port: 1234)

    Client->>Server: DatagramPacket (data: "Hello", IP: localhost, port: 1234)
    #Server-->>Server: Process data

    Server->>Client: DatagramPacket (data: "Hello", IP: ClientIP, port: xxxxx)
    #Client-->>Client: Process response
```
