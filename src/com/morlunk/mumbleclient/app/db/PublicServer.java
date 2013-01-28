package com.morlunk.mumbleclient.app.db;

public class PublicServer {

	private String name;
	private String ca;
	private String continentCode;
	private String country;
	private String countryCode;
	private String ip;
	private Integer port;
	private String region;
	private String url;
	
	public PublicServer(String name, String ca, String continentCode, String country, String countryCode, String ip, Integer port, String region, String url) {
		this.name = name;
		this.ca = ca;
		this.continentCode = continentCode;
		this.country = country;
		this.countryCode = countryCode;
		this.ip = ip;
		this.port = port;
		this.region = region;
		this.url = url;
	}

	public String getName() {
		return name;
	}

	public String getCa() {
		return ca;
	}

	public String getContinentCode() {
		return continentCode;
	}

	public String getCountry() {
		return country;
	}

	public String getCountryCode() {
		return countryCode;
	}

	public String getIp() {
		return ip;
	}

	public Integer getPort() {
		return port;
	}

	public String getRegion() {
		return region;
	}

	public String getUrl() {
		return url;
	}
}
