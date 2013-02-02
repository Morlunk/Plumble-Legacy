package com.morlunk.mumbleclient.app.db;


public class PublicServer extends Server {

	private String ca;
	private String continentCode;
	private String country;
	private String countryCode;
	private String region;
	private String url;
	
	public PublicServer(String name, String ca, String continentCode, String country, String countryCode, String ip, Integer port, String region, String url) {				
		super(-1, name, ip, port, "", "");
		this.ca = ca;
		this.continentCode = continentCode;
		this.country = country;
		this.countryCode = countryCode;
		this.region = region;
		this.url = url;
	}
	
	public String getCA() {
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

	public String getRegion() {
		return region;
	}

	public String getUrl() {
		return url;
	}
}
