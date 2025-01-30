package ro.linic.cloud.service;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import lombok.extern.java.Log;
import ro.linic.cloud.entity.SyncConnection;
import ro.linic.cloud.pojo.Product;
import ro.linic.cloud.pojo.ProductWrapper;
import ro.linic.cloud.pojo.Products;

@Log
@Service
public class WoocommerceApiImpl implements WoocommerceApi {

	private static final String WOO_PRODUCTS_SUFFIX = "/wc-api/v2/products";
	
	@Autowired private RestTemplate restTemplate;
	
	@Override
	public List<Product> allProducts(final SyncConnection syncConnection) {
		int pageNo = 1;
		int totalPages = 1;
		
		final List<Product> allProducts = new ArrayList<>();
		final HttpHeaders headers = createHeaders(syncConnection.getWebsiteKey(), syncConnection.getWebsiteSecret());
		
		do {
			final ResponseEntity<Products> result = restTemplate.exchange(syncConnection.getWebsiteUrl()+WOO_PRODUCTS_SUFFIX+"?page="+pageNo,
					HttpMethod.GET, new HttpEntity<Products>(headers), new ParameterizedTypeReference<Products>(){});
			allProducts.addAll(result.getBody().getProducts());
			
			final String wcTotalPages = result.getHeaders().getFirst("X-WC-TotalPages");
			if (wcTotalPages != null)
				totalPages = Integer.parseInt(wcTotalPages);
				
			pageNo++;
		} while(pageNo <= totalPages);
		
		return allProducts;
	}
	
	@Override
	public ResponseEntity<Product> createProduct(final SyncConnection syncConnection, final Product wooProduct) {
		final HttpHeaders headers = createHeaders(syncConnection.getWebsiteKey(), syncConnection.getWebsiteSecret());
		
		final Map<String, Object> jsonMap = Map.of(Product.WOO_BARCODE_KEY, wooProduct.getBarcode(),
				Product.WOO_NAME_KEY, wooProduct.getName(),
				Product.WOO_PRICE_KEY, wooProduct.getPricePerUom(),
				"catalog_visibility", "hidden",
				"managing_stock", true,
				"backorders", "notify");
		final Map<String, Map<String, Object>> jsonWrapper = Map.of("product", jsonMap);
		
		try {
			final ResponseEntity<ProductWrapper> response = restTemplate.exchange(syncConnection.getWebsiteUrl()+WOO_PRODUCTS_SUFFIX,
					HttpMethod.POST, new HttpEntity<Map<String, Map<String, Object>>>(jsonWrapper, headers),
					new ParameterizedTypeReference<ProductWrapper>(){});
			return unwrap(response);
		} catch (final Exception e) {
			log.severe("Failed createProduct WOO invocation "+jsonMap+" for: "+wooProduct);
			log.log(Level.SEVERE, e.getMessage(), e);
			return ResponseEntity.internalServerError().build();
		}
	}
	
	@Override
	public ResponseEntity<Product> putProduct(final SyncConnection syncConnection, final Product wooProduct) {
		final HttpHeaders headers = createHeaders(syncConnection.getWebsiteKey(), syncConnection.getWebsiteSecret());
		
		final Map<String, Object> jsonMap = new HashMap<>();
		jsonMap.put(Product.WOO_BARCODE_KEY, wooProduct.getBarcode());
		jsonMap.put(Product.WOO_NAME_KEY, wooProduct.getName());
		jsonMap.put(Product.WOO_PRICE_KEY, wooProduct.getPricePerUom());
		jsonMap.put(Product.WOO_STOCK_KEY, wooProduct.getStock());
		final Map<String, Map<String, Object>> jsonWrapper = Map.of("product", jsonMap);
		
		try {
			final ResponseEntity<ProductWrapper> response = restTemplate.exchange(syncConnection.getWebsiteUrl()+WOO_PRODUCTS_SUFFIX+"/"+wooProduct.getId(),
					HttpMethod.PUT, new HttpEntity<Map<String, Map<String, Object>>>(jsonWrapper, headers),
					new ParameterizedTypeReference<ProductWrapper>(){});
			return unwrap(response);
		} catch (final Exception e) {
			log.severe("Failed putProduct WOO invocation "+jsonMap+" for: "+wooProduct);
			log.log(Level.SEVERE, e.getMessage(), e);
			return ResponseEntity.internalServerError().build();
		}
	}
	
	@Override
	public ResponseEntity<Product> patchProduct(final SyncConnection syncConnection, final Product wooProduct) {
		final HttpHeaders headers = createHeaders(syncConnection.getWebsiteKey(), syncConnection.getWebsiteSecret());
		
		final Map<String, Object> jsonMap = new HashMap<>();
		if (wooProduct.getBarcode() != null)
			jsonMap.put(Product.WOO_BARCODE_KEY, wooProduct.getBarcode());
		if (wooProduct.getName() != null)
			jsonMap.put(Product.WOO_NAME_KEY, wooProduct.getName());
		if (wooProduct.getPricePerUom() != null)
			jsonMap.put(Product.WOO_PRICE_KEY, wooProduct.getPricePerUom());
		if (wooProduct.getStock() != null)
			jsonMap.put(Product.WOO_STOCK_KEY, wooProduct.getStock());
		final Map<String, Map<String, Object>> jsonWrapper = Map.of("product", jsonMap);
		
		try {
			final ResponseEntity<ProductWrapper> response = restTemplate.exchange(syncConnection.getWebsiteUrl()+WOO_PRODUCTS_SUFFIX+"/"+wooProduct.getId(),
					HttpMethod.PUT, new HttpEntity<Map<String, Map<String, Object>>>(jsonWrapper, headers),
					new ParameterizedTypeReference<ProductWrapper>(){});
			return unwrap(response);
		} catch (final Exception e) {
			log.severe("Failed patchProduct WOO invocation "+jsonMap+" for: "+wooProduct);
			log.log(Level.SEVERE, e.getMessage(), e);
			return ResponseEntity.internalServerError().build();
		}
	}
	
	@Override
	public ResponseEntity<Product> deactivateProduct(final SyncConnection syncConnection, final int wooId) {
		final HttpHeaders headers = createHeaders(syncConnection.getWebsiteKey(), syncConnection.getWebsiteSecret());
		
		final Map<String, Object> jsonMap = Map.of("catalog_visibility", "hidden");
		final Map<String, Map<String, Object>> jsonWrapper = Map.of("product", jsonMap);
		
		try {
			final ResponseEntity<ProductWrapper> response = restTemplate.exchange(syncConnection.getWebsiteUrl()+WOO_PRODUCTS_SUFFIX+"/"+wooId,
					HttpMethod.PUT, new HttpEntity<Map<String, Map<String, Object>>>(jsonWrapper, headers),
					new ParameterizedTypeReference<ProductWrapper>(){});
			return unwrap(response);
		} catch (final Exception e) {
			log.severe("Failed deactivateProduct WOO invocation "+jsonMap+" for: "+wooId);
			log.log(Level.SEVERE, e.getMessage(), e);
			return ResponseEntity.internalServerError().build();
		}
	}
	
	private ResponseEntity<Product> unwrap(final ResponseEntity<ProductWrapper> response) {
		if (response.hasBody())
			return new ResponseEntity<>(response.getBody().getProduct(), response.getStatusCode());
		
		return new ResponseEntity<>((Product) null, response.getStatusCode());
	}
	
	private HttpHeaders createHeaders(final String apiKey, final String apiSecret) {
		return new HttpHeaders() {{
			final String auth = apiKey + ":" + apiSecret;
			final byte[] encodedAuth = Base64.getEncoder().encode( 
					auth.getBytes(Charset.forName("US-ASCII")) );
			final String authHeader = "Basic " + new String( encodedAuth );
			set( "Authorization", authHeader );
		}};
	}
}
