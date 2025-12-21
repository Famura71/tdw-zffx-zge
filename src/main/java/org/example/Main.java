package org.example;

import com.example.dashboard.Middleware.DashboardBridgeServer;

public class Main {
    public static void main(String[] args) {
        int port = 8080;
        try {
            DashboardBridgeServer server = new DashboardBridgeServer(port);
            server.start();
            System.out.println("Dashboard running on http://localhost:" + port + "/");
            System.out.println("SSE: http://localhost:" + port + "/api/events");
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }
}
