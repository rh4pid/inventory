package com.ultimate.inventory;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class InventoryApplication {

	public static void main(String[] args) {
		// Hikari pings a connection before handing it out if it sat idle > 500 ms. With a remote database
		// that ping costs a full round trip on almost every request; keepalive-time covers dead connections instead.
		System.setProperty("com.zaxxer.hikari.aliveBypassWindowMs", "30000");
		SpringApplication.run(InventoryApplication.class, args);
	}

}
