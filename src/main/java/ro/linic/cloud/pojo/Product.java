package ro.linic.cloud.pojo;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class Product {

	public static final String WOO_BARCODE_KEY = "sku";
	public static final String WOO_NAME_KEY = "title";
	public static final String WOO_PRICE_KEY = "regular_price";
	public static final String WOO_STOCK_KEY = "stock_quantity";
	
	private Integer id;
	private Company company;
	
	@JsonProperty("barcode")    
	@JsonAlias({"barcode", WOO_BARCODE_KEY})
	private String barcode;
	
	@JsonProperty("name")    
	@JsonAlias({"name", WOO_NAME_KEY})
	private String name;
	private String uom;
	
	@JsonProperty("pricePerUom")    
	@JsonAlias({"pricePerUom", WOO_PRICE_KEY})
	private BigDecimal pricePerUom;
	
	@JsonProperty("stock")    
	@JsonAlias({"stock", WOO_STOCK_KEY, "stocuri"})
	private BigDecimal stock;
	
	private Boolean visible;
}