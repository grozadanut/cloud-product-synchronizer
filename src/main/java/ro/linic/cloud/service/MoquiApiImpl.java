package ro.linic.cloud.service;

import java.nio.charset.Charset;
import java.util.Base64;
import java.util.Map;
import java.util.logging.Level;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import lombok.extern.java.Log;
import ro.linic.cloud.command.ChangeNameCommand;
import ro.linic.cloud.command.ChangePriceCommand;
import ro.linic.cloud.command.CreateProductCommand;
import ro.linic.cloud.command.DeleteProductCommand;

@Log
@Service
public class MoquiApiImpl implements MoquiApi {
	@Value("${moqui.url:http://localhost:8085}") private String moquiUrl;
	@Value("${moqui.user}") private String moquiUser;
	@Value("${moqui.pass}") private String moquiPass;
	
	@Value("${moqui.ignoreid:-1}") private String ignoreid;
	@Value("${moqui.ignoreall:false}") private boolean ignoreall;
	
	@Autowired private RestTemplate restTemplate;
	
	private static String mapUom(final String uom) {
        switch (uom) {
            case "BAX":
            case "CUT":
            case "PAC":
            case "SET":
            case "SUL":
                return "OTH_ea";
            case "BUC":
            case "buc":
            case "LEI":
            case "PAL":
            case "PLACA":
            case "PER":
            case "PRET":
            case "RAND":
            case "SAC":
                return "OTH_ea";
            case "KG":
                return "WT_kg";
            case "KM":
                return "LEN_km";
            case "L":
                return "VLIQ_L";
            case "M":
            case "ML":
                return "LEN_m";
            case "MC":
                return "VDRY_m3";
            case "MP":
            case "mp":
            case "M2":
                return "AREA_m2";
            case "ORE":
                return "TF_hr";
            case "T":
            case "TO":
                return "WT_mt";
            default:
                throw new IllegalArgumentException("Unexpected value: " + uom);
        }
    }
	
	@Override
	public ResponseEntity<String> createProduct(CreateProductCommand command) {
		final HttpHeaders headers = createHeaders();
		
		if (ignoreall)
			return ResponseEntity.ok(null);
		if (ignoreid != null && !ignoreid.isEmpty() && ignoreid.equals(command.getProductId().toString()))
			return ResponseEntity.ok(null);

		final Map<String, Object> jsonProductMap = Map.of("productId", command.getProductId(),
				"productTypeEnumId", "PtAsset", "productName", command.getName(),
				"amountUomId", mapUom(command.getUom()), "assetTypeEnumId", "AstTpInventory",
				"assetClassEnumId", "AsClsInventoryFin");
		
		final Map<String, Object> jsonIdMap = Map.of("productId", command.getProductId(),
				"productIdTypeEnumId", "PidtSku", "idValue", command.getBarcode());
		
		final String productPriceId = "leg"+command.getProductId();
		final Map<String, Object> jsonPriceMap = Map.of("productId", command.getProductId(),
				"productPriceId", productPriceId, "priceTypeEnumId", "PptList",
				"pricePurposeEnumId", "PppPurchase", "priceUomId", "RON",
				"price", command.getPricePerUom());
		
		try {
			final ResponseEntity<String> response = restTemplate.exchange(
					moquiUrl + "/rest/s1/mantle/products/" + command.getProductId(), HttpMethod.PATCH,
					new HttpEntity<Map<String, Object>>(jsonProductMap, headers),
					String.class);
			if (response.getStatusCode().isError())
				return response;
			
			final ResponseEntity<String> idResponse = restTemplate.exchange(
					moquiUrl + "/rest/s1/mantle/products/" + command.getProductId() + "/identifications", HttpMethod.POST,
					new HttpEntity<Map<String, Object>>(jsonIdMap, headers),
					String.class);
			if (idResponse.getStatusCode().isError())
				return idResponse;
			
			return restTemplate.exchange(
					moquiUrl + "/rest/s1/mantle/products/" + command.getProductId() + "/prices", HttpMethod.POST,
					new HttpEntity<Map<String, Object>>(jsonPriceMap, headers),
					String.class);
		} catch (final Exception e) {
			log.severe("Failed createProduct Moqui invocation for: " + command);
			log.log(Level.SEVERE, e.getMessage(), e);
			return ResponseEntity.internalServerError().build();
		}
	}
	
	@Override
	public ResponseEntity<String> updateName(ChangeNameCommand command) {
		final HttpHeaders headers = createHeaders();
		
		if (ignoreall)
			return ResponseEntity.ok(null);
		if (ignoreid != null && !ignoreid.isEmpty() && ignoreid.equals(command.getProductId().toString()))
			return ResponseEntity.ok(null);

		final Map<String, Object> jsonProductMap = Map.of("productId", command.getProductId(),
				"productName", command.getName());
		
		try {
			return restTemplate.exchange(
					moquiUrl + "/rest/s1/mantle/products/" + command.getProductId(), HttpMethod.PATCH,
					new HttpEntity<Map<String, Object>>(jsonProductMap, headers),
					String.class);
		} catch (final Exception e) {
			log.severe("Failed updateName Moqui invocation for: " + command);
			log.log(Level.SEVERE, e.getMessage(), e);
			return ResponseEntity.internalServerError().build();
		}
	}
	
	@Override
	public ResponseEntity<String> updatePrice(ChangePriceCommand command) {
		final HttpHeaders headers = createHeaders();
		
		if (ignoreall)
			return ResponseEntity.ok(null);
		if (ignoreid != null && !ignoreid.isEmpty() && ignoreid.equals(command.getProductId().toString()))
			return ResponseEntity.ok(null);

		final String productPriceId = "leg"+command.getProductId();
		final Map<String, Object> jsonPriceMap = Map.of("productId", command.getProductId(),
				"productPriceId", productPriceId, "priceTypeEnumId", "PptList",
				"pricePurposeEnumId", "PppPurchase", "priceUomId", "RON",
				"price", command.getPricePerUom());
		
		try {
			return restTemplate.exchange(
					moquiUrl + "/rest/s1/mantle/products/" + command.getProductId() + "/prices/" + productPriceId, HttpMethod.PATCH,
					new HttpEntity<Map<String, Object>>(jsonPriceMap, headers),
					String.class);
		} catch (final Exception e) {
			log.severe("Failed updatePrice Moqui invocation for: " + command);
			log.log(Level.SEVERE, e.getMessage(), e);
			return ResponseEntity.internalServerError().build();
		}
	}
	
	@Override
	public ResponseEntity<String> deleteProduct(DeleteProductCommand command) {
		final HttpHeaders headers = createHeaders();
		
		if (ignoreall)
			return ResponseEntity.ok(null);
		if (ignoreid != null && !ignoreid.isEmpty() && ignoreid.equals(command.getProductId().toString()))
			return ResponseEntity.ok(null);

		try {
			return restTemplate.exchange(
					moquiUrl + "/rest/s1/mantle/products/" + command.getProductId(), HttpMethod.DELETE,
					new HttpEntity<>(headers),
					String.class);
		} catch (final Exception e) {
			log.severe("Failed deleteProduct Moqui invocation for: " + command);
			log.log(Level.SEVERE, e.getMessage(), e);
			return ResponseEntity.internalServerError().build();
		}
	}
	
	private HttpHeaders createHeaders() {
		return new HttpHeaders() {{
			final String auth = moquiUser + ":" + moquiPass;
			final byte[] encodedAuth = Base64.getEncoder().encode(
					auth.getBytes(Charset.forName("US-ASCII")) );
			final String authHeader = "Basic " + new String(encodedAuth);
			set("Authorization", authHeader);
			set(HttpHeaders.CONTENT_TYPE, "application/json");
			set(HttpHeaders.ACCEPT, "application/json");
		}};
	}
}
