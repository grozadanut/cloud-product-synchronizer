package ro.linic.cloud.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.BDDMockito;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import ro.linic.cloud.entity.SyncConnection;
import ro.linic.cloud.pojo.Product;
import ro.linic.cloud.pojo.Products;

@ExtendWith(MockitoExtension.class)
public class WoocommerceApiTest {

	@Mock private RestTemplate restTemplate;
	@InjectMocks private WoocommerceApiImpl woocommerceApi;
	
	@SuppressWarnings("unchecked")
	@Test
	public void allProducts_whenRestTemplateIsMocked_returnsProductsFromAllPages() {
		// given
		final SyncConnection conn = new SyncConnection();
		conn.setWebsiteUrl("https://woo.com");
		
		final HttpHeaders responseHeaders = new HttpHeaders();
		responseHeaders.set("X-WC-TotalPages", "2");
				   
		final Products productsPage1 = new Products();
		productsPage1.setProducts(new ArrayList<>());
		productsPage1.getProducts().add(Product.builder().id(1).barcode("123").name("powder").build());
		productsPage1.getProducts().add(Product.builder().id(2).barcode("1234").name("lantern").build());
		
		BDDMockito.given(restTemplate.exchange(eq("https://woo.com/wc-api/v2/products?page=1"),
				any(), any(), ArgumentMatchers.isA(ParameterizedTypeReference.class)))
		.willReturn(new ResponseEntity<Products>(productsPage1 , responseHeaders, HttpStatus.OK));
		
		final Products productsPage2 = new Products();
		productsPage2.setProducts(new ArrayList<>());
		productsPage2.getProducts().add(Product.builder().id(34).barcode("854").name("cement").build());
		productsPage2.getProducts().add(Product.builder().id(56).barcode("asdc").name("gloves").build());
		
		BDDMockito.given(restTemplate.exchange(eq("https://woo.com/wc-api/v2/products?page=2"),
				any(), any(), ArgumentMatchers.isA(ParameterizedTypeReference.class)))
		.willReturn(new ResponseEntity<Products>(productsPage2 , responseHeaders, HttpStatus.OK));
		
		final List<Product> allWooProducts = new ArrayList<>(productsPage1.getProducts());
		allWooProducts.addAll(productsPage2.getProducts());
		
		// when
		final List<Product> returnedProducts = woocommerceApi.allProducts(conn);
		
		// then
		assertThat(returnedProducts).containsAll(allWooProducts);
	}
	
	@SuppressWarnings("unchecked")
	@Test
	public void allProducts_whenRestTemplateIsMocked_authHeaderShouldBeSet() {
		// given
		final SyncConnection conn = new SyncConnection();
		conn.setWebsiteUrl("https://woo.com");
		conn.setWebsiteSecret("theSecret");
		conn.setWebsiteKey("theKey");
		
		final String auth = "theKey:theSecret";
		final byte[] encodedAuth = Base64.getEncoder().encode( 
				auth.getBytes(Charset.forName("US-ASCII")) );
		final String authHeader = "Basic " + new String( encodedAuth );
		
		final HttpHeaders responseHeaders = new HttpHeaders();
		responseHeaders.set("X-WC-TotalPages", "2");
		
		final Products empty = new Products();
		empty.setProducts(new ArrayList<>());
		when(restTemplate.exchange(eq("https://woo.com/wc-api/v2/products?page=1"),
				any(), any(), ArgumentMatchers.isA(ParameterizedTypeReference.class)))
		.thenReturn(new ResponseEntity<Products>(empty, responseHeaders, HttpStatus.OK));
		
		when(restTemplate.exchange(eq("https://woo.com/wc-api/v2/products?page=2"),
				any(), any(), ArgumentMatchers.isA(ParameterizedTypeReference.class)))
		.thenReturn(new ResponseEntity<Products>(empty, responseHeaders, HttpStatus.OK));
		
		// when
		woocommerceApi.allProducts(conn);
		
		// then
		final ArgumentCaptor<HttpEntity> notifCaptorPage1 = ArgumentCaptor.forClass(HttpEntity.class);
		verify(restTemplate).exchange(eq("https://woo.com/wc-api/v2/products?page=1"),
				any(), notifCaptorPage1.capture(), ArgumentMatchers.isA(ParameterizedTypeReference.class));
		
		final HttpEntity capturedHttpEntityPage1 = notifCaptorPage1.getValue();
		assertThat(capturedHttpEntityPage1.getHeaders().get("Authorization")).containsExactly(authHeader);
		
		final ArgumentCaptor<HttpEntity> notifCaptorPage2 = ArgumentCaptor.forClass(HttpEntity.class);
		verify(restTemplate).exchange(eq("https://woo.com/wc-api/v2/products?page=2"),
				any(), notifCaptorPage2.capture(), ArgumentMatchers.isA(ParameterizedTypeReference.class));
		
		final HttpEntity capturedHttpEntityPage2 = notifCaptorPage2.getValue();
		assertThat(capturedHttpEntityPage2.getHeaders().get("Authorization")).containsExactly(authHeader);
	}
}
