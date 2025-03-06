package ro.linic.cloud.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import ro.linic.cloud.entity.SyncConnection;
import ro.linic.cloud.pojo.Product;
import ro.linic.cloud.pojo.Products;

@SpringBootTest
@ActiveProfiles("test")
public class WoocommerceApiIntegrationTest {
	@Autowired private WoocommerceApi woocommerceApi;
    @Autowired private RestTemplate restTemplate;
    
    private MockRestServiceServer mockServer;
    
    @BeforeEach
    public void init() {
        mockServer = MockRestServiceServer.createServer(restTemplate);
    }
    
    @Test
	public void allProducts_whenWebsiteIsMocked_parsesJsonAndReturnsAllWooProducts() throws URISyntaxException {
		// given
		final SyncConnection conn = new SyncConnection();
		conn.setWebsiteUrl("https://woo.com");
		
		mockServer.expect(ExpectedCount.once(), 
				requestTo(new URI("https://woo.com/wc-api/v2/products?page=1")))
		.andExpect(method(HttpMethod.GET))
		.andRespond(withStatus(HttpStatus.OK)
				.header("X-WC-TotalPages", "2")
				.contentType(MediaType.APPLICATION_JSON)
				.body("""
						{
						  "products": [
						    {
						      "title": "powder",
						      "id": 1,
						      "created_at": "2015-01-22T19:46:16Z",
						      "type": "simple",
						      "sku": "123",
						      "price": "21.99",
						      "regular_price": "21.99",
						      "sale_price": null,
						      "managing_stock": false,
						      "stock_quantity": 10.25,
						      "in_stock": true,
						      "backorders_allowed": false,
						      "backordered": false,
						      "sold_individually": false,
						      "purchaseable": true,
						      "visible": false,
						      "catalog_visibility": "hidden",
						      "dimensions": {
						        "length": "",
						        "width": "",
						        "height": "",
						        "unit": "kg"
						      }
						    },
						    {
						      "title": "lantern",
						      "id": 2,
						      "sku": "1234",
						      "price": "55",
						      "regular_price": "55",
						      "managing_stock": false,
						      "stock_quantity": 2,
						      "in_stock": true,
						      "visible": true,
						      "catalog_visibility": "visible",
						      "dimensions": {
						        "unit": "buc"
						      }
						    }
						  ]
						}
						""")
				);
		
		mockServer.expect(ExpectedCount.once(), 
				requestTo(new URI("https://woo.com/wc-api/v2/products?page=2")))
		.andExpect(method(HttpMethod.GET))
		.andRespond(withStatus(HttpStatus.OK)
				.header("X-WC-TotalPages", "2")
				.contentType(MediaType.APPLICATION_JSON)
				.body("""
						{
						  "products": [
						    {
						      "title": "cement",
						      "id": 34,
						      "sku": "854",
						      "price": "29.5",
						      "regular_price": "29.5",
						      "managing_stock": false,
						      "stock_quantity": 654,
						      "in_stock": true,
						      "visible": true,
						      "catalog_visibility": "visible",
						      "dimensions": {
						        "unit": "buc"
						      }
						    },
						    {
						      "title": "gloves",
						      "id": 56,
						      "sku": "asdc",
						      "price": "6",
						      "regular_price": "6",
						      "managing_stock": false,
						      "stock_quantity": 12,
						      "in_stock": true,
						      "visible": true,
						      "catalog_visibility": "visible",
						      "dimensions": {
						        "unit": "set"
						      }
						    }
						  ]
						}
						""")
				);
		
		// when
		final List<Product> returnedProducts = woocommerceApi.allProducts(conn);
		
		// then
		final Products productsPage1 = new Products();
		productsPage1.setProducts(new ArrayList<>());
		productsPage1.getProducts().add(Product.builder().id(1).barcode("123").name("powder")
				.pricePerUom(new BigDecimal("21.99"))
				.stock(new BigDecimal("10.25"))
				.visible(false)
				.build());
		productsPage1.getProducts().add(Product.builder().id(2).barcode("1234").name("lantern")
				.pricePerUom(new BigDecimal("55"))
				.stock(new BigDecimal("2"))
				.visible(true)
				.build());
		
		final Products productsPage2 = new Products();
		productsPage2.setProducts(new ArrayList<>());
		productsPage2.getProducts().add(Product.builder().id(34).barcode("854").name("cement")
				.pricePerUom(new BigDecimal("29.5"))
				.stock(new BigDecimal("654"))
				.visible(true)
				.build());
		productsPage2.getProducts().add(Product.builder().id(56).barcode("asdc").name("gloves")
				.pricePerUom(new BigDecimal("6"))
				.stock(new BigDecimal("12"))
				.visible(true)
				.build());
		
		final List<Product> allWooProducts = new ArrayList<>(productsPage1.getProducts());
		allWooProducts.addAll(productsPage2.getProducts());
		
		mockServer.verify();
		assertThat(returnedProducts).containsAll(allWooProducts);
	}
    
    @Test
	public void createProduct_whenProductIsNull_throwNPE() {
    	final SyncConnection conn = new SyncConnection();
		conn.setWebsiteUrl("https://woo.com");
		
		assertThrows(NullPointerException.class, () -> woocommerceApi.createProduct(conn, null));
    }
    
    @Test
   	public void createProduct_whenWebsiteIsMocked_returnCreatedWooProduct() throws URISyntaxException {
    	// given
       	final SyncConnection conn = new SyncConnection();
   		conn.setWebsiteUrl("https://woo.com");
   		
   		final Product product = Product.builder().barcode("854").name("cement 40kg")
				.pricePerUom(new BigDecimal("29.5"))
				.build();
   		
   		mockServer.expect(ExpectedCount.once(), 
				requestTo(new URI("https://woo.com/wc-api/v2/products")))
		.andExpect(method(HttpMethod.POST))
		.andExpect(content().json("""
				{
				  "product": {
				    "title": "cement 40kg",
				    "regular_price": 29.5,
				    "sku": "854",
				    "catalog_visibility": "hidden",
				    "managing_stock": true,
				    "backorders": "notify"
				  }
				}
				"""))
		.andRespond(withStatus(HttpStatus.OK)
				.contentType(MediaType.APPLICATION_JSON)
				.body("""
						{
						  "product":
						    {
						      "title": "cement 40kg",
						      "id": 34,
						      "sku": "854",
						      "price": "29.5",
						      "regular_price": "29.5",
						      "visible": false,
						      "catalog_visibility": "hidden"
						    }
						}
						""")
				);
   		
   		// when
   		final ResponseEntity<Product> wooResponse = woocommerceApi.createProduct(conn, product);
   		
   		// then
   		mockServer.verify();
   		assertThat(wooResponse.getBody().getId()).isEqualTo(34);
   		assertThat(wooResponse.getBody().getBarcode()).isEqualTo(product.getBarcode());
   		assertThat(wooResponse.getBody().getName()).isEqualTo(product.getName());
   		assertThat(wooResponse.getBody().getPricePerUom()).isEqualByComparingTo(product.getPricePerUom());
   		assertThat(wooResponse.getBody().getVisible()).isFalse();
    }
    
    @Test
   	public void putProduct_whenProductExists_overrideAllProductFields() throws URISyntaxException {
    	// given
       	final SyncConnection conn = new SyncConnection();
   		conn.setWebsiteUrl("https://woo.com");
   		
   		final Product product = Product.builder().id(45).barcode("854").name("cement 40kg")
				.stock(new BigDecimal("44"))
				.build();
   		
   		mockServer.expect(ExpectedCount.once(), 
				requestTo(new URI("https://woo.com/wc-api/v2/products/45")))
		.andExpect(method(HttpMethod.PUT))
		.andExpect(content().json("""
				{
				  "product": {
				    "title": "cement 40kg",
				    "regular_price": null,
				    "sku": "854",
				    "stock_quantity": 44
				  }
				}
				"""))
		.andRespond(withStatus(HttpStatus.OK)
				.contentType(MediaType.APPLICATION_JSON)
				.body("""
						{
						  "product":
						    {
						      "title": "cement 40kg",
						      "id": 45,
						      "sku": "854",
						      "price": null,
						      "regular_price": null,
						      "stock_quantity": 44
						    }
						}
						""")
				);
   		
   		// when
   		final ResponseEntity<Product> wooResponse = woocommerceApi.putProduct(conn, product);
   		
   		// then
   		mockServer.verify();
   		assertThat(wooResponse.getBody().getId()).isEqualTo(product.getId());
   		assertThat(wooResponse.getBody().getBarcode()).isEqualTo(product.getBarcode());
   		assertThat(wooResponse.getBody().getName()).isEqualTo(product.getName());
   		assertThat(wooResponse.getBody().getPricePerUom()).isNull();
   		assertThat(wooResponse.getBody().getStock()).isEqualByComparingTo(product.getStock());
    }
    
    @Test
   	public void patchProduct_whenProductExists_overrideNonNullFields() throws URISyntaxException {
    	// given
       	final SyncConnection conn = new SyncConnection();
   		conn.setWebsiteUrl("https://woo.com");
   		
   		final Product product = Product.builder().id(45).barcode("854").name("cement 40kilograms")
				.build();
   		
   		mockServer.expect(ExpectedCount.once(), 
				requestTo(new URI("https://woo.com/wc-api/v2/products/45")))
		.andExpect(method(HttpMethod.PUT))
		.andExpect(content().json("""
				{
				  "product": {
				    "title": "cement 40kilograms",
				    "sku": "854"
				  }
				}
				"""))
		.andRespond(withStatus(HttpStatus.OK)
				.contentType(MediaType.APPLICATION_JSON)
				.body("""
						{
						  "product":
						    {
						      "title": "cement 40kilograms",
						      "id": 45,
						      "sku": "854",
						      "price": 29.5,
						      "regular_price": 29.5,
						      "stock_quantity": 44
						    }
						}
						""")
				);
   		
   		// when
   		final ResponseEntity<Product> wooResponse = woocommerceApi.patchProduct(conn, product);
   		
   		// then
   		mockServer.verify();
   		assertThat(wooResponse.getBody().getId()).isEqualTo(product.getId());
   		assertThat(wooResponse.getBody().getBarcode()).isEqualTo(product.getBarcode());
   		assertThat(wooResponse.getBody().getName()).isEqualTo(product.getName());
   		assertThat(wooResponse.getBody().getPricePerUom()).isEqualByComparingTo(new BigDecimal("29.5"));
   		assertThat(wooResponse.getBody().getStock()).isEqualByComparingTo(new BigDecimal("44"));
    }
    
    @Test
   	public void deactivateProduct_whenProductExists_hideWooProduct() throws URISyntaxException {
    	// given
       	final SyncConnection conn = new SyncConnection();
   		conn.setWebsiteUrl("https://woo.com");
   		
   		final Product product = Product.builder().id(45).barcode("854").name("cement 40kilograms")
   				.pricePerUom(new BigDecimal("29.5"))
   				.stock(new BigDecimal("145"))
				.build();
   		
   		mockServer.expect(ExpectedCount.once(), 
				requestTo(new URI("https://woo.com/wc-api/v2/products/45")))
		.andExpect(method(HttpMethod.PUT))
		.andExpect(content().json("""
				{
				  "product": {
				    "catalog_visibility": "hidden"
				  }
				}
				"""))
		.andRespond(withStatus(HttpStatus.OK)
				.contentType(MediaType.APPLICATION_JSON)
				.body("""
						{
						  "product":
						    {
						      "title": "cement 40kilograms",
						      "id": 45,
						      "sku": "854",
						      "price": 29.5,
						      "regular_price": 29.5,
						      "stock_quantity": 145,
						      "visible": false,
						      "catalog_visibility": "hidden"
						    }
						}
						""")
				);
   		
   		// when
   		final ResponseEntity<Product> wooResponse = woocommerceApi.deactivateProduct(conn, product.getId());
   		
   		// then
   		mockServer.verify();
   		assertThat(wooResponse.getBody().getVisible()).isFalse();
   		assertThat(wooResponse.getBody().getId()).isEqualTo(product.getId());
   		assertThat(wooResponse.getBody().getBarcode()).isEqualTo(product.getBarcode());
   		assertThat(wooResponse.getBody().getName()).isEqualTo(product.getName());
   		assertThat(wooResponse.getBody().getPricePerUom()).isEqualByComparingTo(product.getPricePerUom());
   		assertThat(wooResponse.getBody().getStock()).isEqualByComparingTo(product.getStock());
    }
}
