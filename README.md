## FastSync server

The project includes a simple Ktor server for [FastSync](https://github.com/parneet-guraya/FastSync). The server provides the following functionalities:

1. **File Management:**  
   The server handles shares storage by saving a share(name,path) added from either client (mobile or desktop) to a local directory and recording their details in an SQLite database.

2. **WebSocket Communication:**  
   The server also supports WebSocket connections, facilitating real-time communication between the mobile and desktop clients. This is crucial for coordinating file transfers, right now being used for when the desktop client needs to push files to the mobile device.

3. **Client Integration:**  
   - **Mobile Client:** Adds files to the server and queries the database for files available on the desktop and uses WebSocket to send request data.
   - **Desktop Client:** Adds files to the server, queries the database for files available on the mobile, and uses WebSocket to receive transfer instructions.

The Ktor server ensures seamless interaction between the clients, enabling efficient and coordinated file transfers in both directions.
