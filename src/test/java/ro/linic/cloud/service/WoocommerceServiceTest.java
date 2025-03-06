package ro.linic.cloud.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.endsWith;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.AdditionalAnswers;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.hateoas.client.Traverson;
import org.springframework.hateoas.client.Traverson.TraversalBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import ro.linic.cloud.command.ChangeNameCommand;
import ro.linic.cloud.command.ChangePriceCommand;
import ro.linic.cloud.command.ChangeStockCommand;
import ro.linic.cloud.command.CreateNotificationCommand;
import ro.linic.cloud.command.CreateProductCommand;
import ro.linic.cloud.command.DeleteProductCommand;
import ro.linic.cloud.entity.SyncConnection;
import ro.linic.cloud.entity.SyncLine;
import ro.linic.cloud.pojo.Product;
import ro.linic.cloud.repository.SyncConnectionRepository;
import ro.linic.cloud.repository.SyncProductRepository;

@ExtendWith(MockitoExtension.class)
public class WoocommerceServiceTest {

	@Mock private WoocommerceApi woocommerceApi;
	@Mock private SyncConnectionRepository syncConnRepo;
	@Mock private SyncProductRepository syncProductRepo;
	@Mock private RestTemplate restTemplate;
	@Mock private Traverson traversonMock;
	@Mock private TraversalBuilder traversonBuilderMock;
	@Spy @InjectMocks private WoocommerceServiceImpl woocommerceService;
	
	private final Integer companyId = 1;
	private final SyncConnection syncConnection =
			new SyncConnection(1, companyId, "http://inventory.linic.ro", "http://linic.ro", "key", "secret");
	
	@BeforeEach
	public void setup() {
		lenient().when(syncConnRepo.save(any())).then(AdditionalAnswers.returnsFirstArg());
		lenient().when(syncProductRepo.save(any())).then(AdditionalAnswers.returnsFirstArg());
		lenient().when(syncConnRepo.findByCompanyId(syncConnection.getCompanyId()))
		.thenReturn(Optional.of(syncConnection));
	}
	
	@Test
	public void createProduct_whenCommandIsValid_createSyncLine() {
		// given
		final CreateProductCommand command = new CreateProductCommand(companyId, 22, "59", "cement 40kg", "buc", new BigDecimal("29.5"));
		final Product commandProd = Product.builder()
				.barcode(command.getBarcode())
				.name(command.getName())
				.uom(command.getUom())
				.pricePerUom(command.getPricePerUom())
				.build();
		final Product wooProd = Product.builder()
				.id(55)
				.barcode(command.getBarcode())
				.name(command.getName())
				.uom(command.getUom())
				.pricePerUom(command.getPricePerUom())
				.build();
				
		when(woocommerceApi.createProduct(any(), eq(commandProd)))
		.thenReturn(new ResponseEntity<Product>(wooProd, HttpStatus.OK));
		
		// when
		final ResponseEntity<Product> result = woocommerceService.createProduct(command);
		
		// then
		assertThat(result.getBody().getId()).isEqualTo(wooProd.getId());
		assertThat(result.getBody().getBarcode()).isEqualTo(wooProd.getBarcode());
		assertThat(result.getBody().getName()).isEqualTo(wooProd.getName());
		assertThat(result.getBody().getUom()).isEqualTo(wooProd.getUom());
		assertThat(result.getBody().getPricePerUom()).isEqualByComparingTo(wooProd.getPricePerUom());
		
		final ArgumentCaptor<SyncLine> syncLineCaptor = ArgumentCaptor.forClass(SyncLine.class);
		verify(syncProductRepo).save(syncLineCaptor.capture());
		final SyncLine savedSyncLine = syncLineCaptor.getValue();
		assertThat(savedSyncLine.getProductId()).isEqualTo(command.getProductId());
		assertThat(savedSyncLine.getWooId()).isEqualTo(wooProd.getId());
		
//		final ArgumentCaptor<HttpEntity<CreateNotificationCommand>> notifCommandCaptor = ArgumentCaptor.forClass(HttpEntity.class);
//		verify(restTemplate).exchange(endsWith("/notification"), eq(HttpMethod.POST),
//				notifCommandCaptor.capture(), eq(Void.class));
//		final HttpEntity<CreateNotificationCommand> notifCommand = notifCommandCaptor.getValue();
//		assertThat(notifCommand.getBody().getNotification()).isEqualTo(
//				"Produs adaugat pe woocommerce" + System.lineSeparator()
//				+ "ID: 55" + System.lineSeparator()
//				+ "Cod: 59" + System.lineSeparator()
//				+ "Nume: cement 40kg" + System.lineSeparator()
//				+ "Pret: 29.5" + System.lineSeparator()
//				+ "Acceseaza http://linic.ro pentru a edita");
	}
	
	@Test
	public void createProduct_whenCommandIsNull_throwNPE() {
		assertThrows(NullPointerException.class, () -> woocommerceService.createProduct(null));
	}
	
	@Test
	public void createProduct_whenWooReturnsErrorCode_returnAccepted() {
		// given
		final CreateProductCommand command = new CreateProductCommand(companyId, 22, "59", "cement 40kg", "buc", new BigDecimal("29.5"));
		final Product commandProd = Product.builder()
				.barcode(command.getBarcode())
				.name(command.getName())
				.uom(command.getUom())
				.pricePerUom(command.getPricePerUom())
				.build();
		
		when(woocommerceApi.createProduct(any(), eq(commandProd)))
		.thenReturn(new ResponseEntity<Product>(HttpStatus.BAD_REQUEST));
		
		// when
		final ResponseEntity<Product> result = woocommerceService.createProduct(command);
		
		// then
		assertThat(result.getBody()).isNull();
		assertThat(result.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
	}
	
	@Test
	public void createProduct_whenSyncConnectionIsMissing_returnEmptyOKResponse() {
		// given
		final CreateProductCommand command = new CreateProductCommand(2, 22, "59", "cement 40kg", "buc", new BigDecimal("29.5"));
		
		// when
		final ResponseEntity<Product> result = woocommerceService.createProduct(command);
		
		// then
		assertThat(result.getBody()).isNull();
		assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
	}
	
	@Test
	public void createProduct_whenSyncLineIsPresent_throwException() {
		// given
		final CreateProductCommand command = new CreateProductCommand(companyId, 22, "59", "cement 40kg", "buc", new BigDecimal("29.5"));
		
		when(syncProductRepo.findBySyncConnectionIdAndProductId(syncConnection.getId(), command.getProductId()))
		.thenReturn(Optional.of(new SyncLine(1L, syncConnection, command.getProductId(), 1, null)));
		
		// when
		// then
		assertThatThrownBy(() -> woocommerceService.createProduct(command))
		.isInstanceOf(RuntimeException.class);
		
	}
	
	@Test
	public void createProduct_whenCommandOptionalFieldsAreNull_createProductWithNullFields() {
		// given
		final CreateProductCommand command = new CreateProductCommand(companyId, 22, null, null, null, null);
		final Product commandProd = Product.builder()
				.barcode(command.getBarcode())
				.name(command.getName())
				.uom(command.getUom())
				.pricePerUom(command.getPricePerUom())
				.build();
		final Product wooProd = Product.builder()
				.id(55)
				.barcode(command.getBarcode())
				.name(command.getName())
				.uom(command.getUom())
				.pricePerUom(command.getPricePerUom())
				.build();
		
		when(woocommerceApi.createProduct(any(), eq(commandProd)))
		.thenReturn(new ResponseEntity<Product>(wooProd, HttpStatus.OK));
		
		// when
		final ResponseEntity<Product> result = woocommerceService.createProduct(command);
		
		// then
		assertThat(result.getBody().getId()).isEqualTo(wooProd.getId());
		assertThat(result.getBody().getBarcode()).isNull();
		assertThat(result.getBody().getName()).isNull();
		assertThat(result.getBody().getUom()).isNull();
		assertThat(result.getBody().getPricePerUom()).isNull();
		
		final ArgumentCaptor<SyncLine> syncLineCaptor = ArgumentCaptor.forClass(SyncLine.class);
		verify(syncProductRepo).save(syncLineCaptor.capture());
		final SyncLine savedSyncLine = syncLineCaptor.getValue();
		assertThat(savedSyncLine.getProductId()).isEqualTo(command.getProductId());
		assertThat(savedSyncLine.getWooId()).isEqualTo(wooProd.getId());
		
//		final ArgumentCaptor<HttpEntity<CreateNotificationCommand>> notifCommandCaptor = ArgumentCaptor.forClass(HttpEntity.class);
//		verify(restTemplate).exchange(endsWith("/notification"), eq(HttpMethod.POST),
//				notifCommandCaptor.capture(), eq(Void.class));
//		final HttpEntity<CreateNotificationCommand> notifCommand = notifCommandCaptor.getValue();
//		assertThat(notifCommand.getBody().getNotification()).isEqualTo(
//				"Produs adaugat pe woocommerce" + System.lineSeparator()
//				+ "ID: 55" + System.lineSeparator()
//				+ "Cod: null" + System.lineSeparator()
//				+ "Nume: null" + System.lineSeparator()
//				+ "Pret: null" + System.lineSeparator()
//				+ "Acceseaza http://linic.ro pentru a edita");
	}
	
	@Test
	public void updatePrice_whenCommandIsValid_updateWooPrice() {
		// given
		final ChangePriceCommand command = new ChangePriceCommand(companyId, 22, new BigDecimal("31"));
		final Product commandProd = Product.builder()
				.id(55)
				.pricePerUom(command.getPricePerUom())
				.build();
		final Product wooProd = Product.builder()
				.id(55)
				.pricePerUom(command.getPricePerUom())
				.build();
		
		when(woocommerceApi.patchProduct(any(), eq(commandProd)))
		.thenReturn(new ResponseEntity<Product>(wooProd, HttpStatus.OK));
		
		when(syncProductRepo.findBySyncConnectionIdAndProductId(syncConnection.getId(), command.getProductId()))
		.thenReturn(Optional.of(new SyncLine(1L, syncConnection, command.getProductId(), wooProd.getId(), null)));
		
		// when
		final ResponseEntity<Product> result = woocommerceService.updatePrice(command);
		
		// then
		assertThat(result.getBody().getId()).isEqualTo(wooProd.getId());
		assertThat(result.getBody().getPricePerUom()).isEqualByComparingTo(wooProd.getPricePerUom());
	}
	
	@Test
	public void updatePrice_whenSyncConnectionIsMissing_returnEmptyOKResponse() {
		// given
		final ChangePriceCommand command = new ChangePriceCommand(2, 22, new BigDecimal("31"));
		
		// when
		final ResponseEntity<Product> result = woocommerceService.updatePrice(command);
		
		// then
		assertThat(result.getBody()).isNull();
		assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
	}
	
	@Test
	public void updatePrice_whenSyncLineIsMissing_returnEmptyOKResponse() {
		// given
		final ChangePriceCommand command = new ChangePriceCommand(companyId, 22, new BigDecimal("31"));
		
		// when
		final ResponseEntity<Product> result = woocommerceService.updatePrice(command);
		
		// then
		assertThat(result.getBody()).isNull();
		assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
	}
	
	@Test
	public void updatePrice_whenPriceIsNull_setWooPriceToNull() {
		// given
		final ChangePriceCommand command = new ChangePriceCommand(companyId, 22, null);
		final Product commandProd = Product.builder()
				.id(55)
				.pricePerUom(command.getPricePerUom())
				.build();
		final Product wooProd = Product.builder()
				.id(55)
				.pricePerUom(command.getPricePerUom())
				.build();
		
		when(woocommerceApi.patchProduct(any(), eq(commandProd)))
		.thenReturn(new ResponseEntity<Product>(wooProd, HttpStatus.OK));
		
		when(syncProductRepo.findBySyncConnectionIdAndProductId(syncConnection.getId(), command.getProductId()))
		.thenReturn(Optional.of(new SyncLine(1L, syncConnection, command.getProductId(), wooProd.getId(), null)));
		
		// when
		final ResponseEntity<Product> result = woocommerceService.updatePrice(command);
		
		// then
		assertThat(result.getBody().getId()).isEqualTo(wooProd.getId());
		assertThat(result.getBody().getPricePerUom()).isNull();
	}
	
	@Test
	public void updateStock_whenCommandIsValid_updateWooStock() {
		// given
		final ChangeStockCommand command = new ChangeStockCommand(companyId, 22, new BigDecimal("650.5"));
		final Product commandProd = Product.builder()
				.id(55)
				.stock(command.getStock())
				.build();
		final Product wooProd = Product.builder()
				.id(55)
				.stock(command.getStock())
				.build();
		
		when(woocommerceApi.patchProduct(any(), eq(commandProd)))
		.thenReturn(new ResponseEntity<Product>(wooProd, HttpStatus.OK));
		
		when(syncProductRepo.findBySyncConnectionIdAndProductId(syncConnection.getId(), command.getProductId()))
		.thenReturn(Optional.of(new SyncLine(1L, syncConnection, command.getProductId(), wooProd.getId(), null)));
		
		// when
		final ResponseEntity<Product> result = woocommerceService.updateStock(command);
		
		// then
		assertThat(result.getBody().getId()).isEqualTo(wooProd.getId());
		assertThat(result.getBody().getStock()).isEqualByComparingTo(wooProd.getStock());
	}
	
	@Test
	public void updateStock_whenSyncConnectionIsMissing_returnEmptyOKResponse() {
		// given
		final ChangeStockCommand command = new ChangeStockCommand(2, 22, new BigDecimal("650.5"));
		
		// when
		final ResponseEntity<Product> result = woocommerceService.updateStock(command);
		
		// then
		assertThat(result.getBody()).isNull();
		assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
	}
	
	@Test
	public void updateStock_whenSyncLineIsMissing_returnEmptyOKResponse() {
		// given
		final ChangeStockCommand command = new ChangeStockCommand(companyId, 22, new BigDecimal("650.5"));
		
		// when
		final ResponseEntity<Product> result = woocommerceService.updateStock(command);
		
		// then
		assertThat(result.getBody()).isNull();
		assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
	}
	
	@Test
	public void updateStock_whenStockIsNull_setWooStockToNull() {
		// given
		final ChangeStockCommand command = new ChangeStockCommand(companyId, 22, null);
		final Product commandProd = Product.builder()
				.id(55)
				.stock(command.getStock())
				.build();
		final Product wooProd = Product.builder()
				.id(55)
				.stock(command.getStock())
				.build();
		
		when(woocommerceApi.patchProduct(any(), eq(commandProd)))
		.thenReturn(new ResponseEntity<Product>(wooProd, HttpStatus.OK));
		
		when(syncProductRepo.findBySyncConnectionIdAndProductId(syncConnection.getId(), command.getProductId()))
		.thenReturn(Optional.of(new SyncLine(1L, syncConnection, command.getProductId(), wooProd.getId(), null)));
		
		// when
		final ResponseEntity<Product> result = woocommerceService.updateStock(command);
		
		// then
		assertThat(result.getBody().getId()).isEqualTo(wooProd.getId());
		assertThat(result.getBody().getStock()).isNull();
	}
	
	@Test
	public void updateName_whenCommandIsValid_notify() {
		// given
		final ChangeNameCommand command = new ChangeNameCommand(companyId, 22, "59", "cement holcim 40kg");
		final int wooId = 55;
		
		when(syncProductRepo.findBySyncConnectionIdAndProductId(syncConnection.getId(), command.getProductId()))
		.thenReturn(Optional.of(new SyncLine(1L, syncConnection, command.getProductId(), wooId, null)));
		
		// when
		woocommerceService.updateName(command);
		
		// then
		final ArgumentCaptor<HttpEntity<CreateNotificationCommand>> notifCommandCaptor = ArgumentCaptor.forClass(HttpEntity.class);
		verify(restTemplate).exchange(endsWith("/notification"), eq(HttpMethod.POST),
				notifCommandCaptor.capture(), eq(Void.class));
		final HttpEntity<CreateNotificationCommand> notifCommand = notifCommandCaptor.getValue();
		assertThat(notifCommand.getBody().getNotification()).isEqualTo(
				"Numele produsului a fost modificat" + System.lineSeparator()
				+ "ID: 22" + System.lineSeparator()
				+ "Woo ID: 55" + System.lineSeparator()
				+ "Cod: 59" + System.lineSeparator()
				+ "Nume nou: cement holcim 40kg" + System.lineSeparator()
				+ "Acceseaza http://linic.ro pentru a edita");
	}
	
	@Test
	public void updateName_whenSyncConnectionIsMissing_returnEmptyOKResponse() {
		// given
		final ChangeNameCommand command = new ChangeNameCommand(2, 22, "59", "cement holcim 40kg");
		
		// when
		final ResponseEntity<Product> result = woocommerceService.updateName(command);
		
		// then
		assertThat(result.getBody()).isNull();
		assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
	}
	
	@Test
	public void updateName_whenSyncLineIsMissing_returnEmptyOKResponse() {
		// given
		final ChangeNameCommand command = new ChangeNameCommand(companyId, 22, "59", "cement holcim 40kg");
		
		// when
		final ResponseEntity<Product> result = woocommerceService.updateName(command);
		
		// then
		assertThat(result.getBody()).isNull();
		assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
	}
	
	@Test
	public void updateName_whenNameIsNull_notifyWithEmptyName() {
		// given
		final ChangeNameCommand command = new ChangeNameCommand(companyId, 22, "59", null);
		final int wooId = 55;
		
		when(syncProductRepo.findBySyncConnectionIdAndProductId(syncConnection.getId(), command.getProductId()))
		.thenReturn(Optional.of(new SyncLine(1L, syncConnection, command.getProductId(), wooId, null)));
		
		// when
		woocommerceService.updateName(command);
		
		// then
		final ArgumentCaptor<HttpEntity<CreateNotificationCommand>> notifCommandCaptor = ArgumentCaptor.forClass(HttpEntity.class);
		verify(restTemplate).exchange(endsWith("/notification"), eq(HttpMethod.POST),
				notifCommandCaptor.capture(), eq(Void.class));
		final HttpEntity<CreateNotificationCommand> notifCommand = notifCommandCaptor.getValue();
		assertThat(notifCommand.getBody().getNotification()).isEqualTo(
				"Numele produsului a fost modificat" + System.lineSeparator()
				+ "ID: 22" + System.lineSeparator()
				+ "Woo ID: 55" + System.lineSeparator()
				+ "Cod: 59" + System.lineSeparator()
				+ "Nume nou: null" + System.lineSeparator()
				+ "Acceseaza http://linic.ro pentru a edita");
	}
	
	@Test
	public void deleteProduct_whenCommandIsValid_deleteSyncLineAndDeactivateWooProduct() {
		// given
		final DeleteProductCommand command = new DeleteProductCommand(companyId, 22);
		final int wooId = 55;
		
		when(syncProductRepo.findBySyncConnectionIdAndProductId(syncConnection.getId(), command.getProductId()))
		.thenReturn(Optional.of(new SyncLine(1L, syncConnection, command.getProductId(), wooId, null)));
		
		// when
		woocommerceService.deleteProduct(command);
		
		// then
		final ArgumentCaptor<SyncLine> syncLineCaptor = ArgumentCaptor.forClass(SyncLine.class);
		verify(syncProductRepo).delete(syncLineCaptor.capture());
		final SyncLine deletedSyncLine = syncLineCaptor.getValue();
		assertThat(deletedSyncLine.getProductId()).isEqualTo(command.getProductId());
		assertThat(deletedSyncLine.getWooId()).isEqualTo(wooId);
		
		final ArgumentCaptor<Integer> wooIdCaptor = ArgumentCaptor.forClass(Integer.class);
		verify(woocommerceApi).deactivateProduct(eq(syncConnection), wooIdCaptor.capture());
		final Integer deactivatedWooId = wooIdCaptor.getValue();
		assertThat(deactivatedWooId).isEqualTo(wooId);
	}
	
	@Test
	public void deleteProduct_whenSyncConnectionIsMissing_returnEmptyOKResponse() {
		// given
		final DeleteProductCommand command = new DeleteProductCommand(2, 22);
		
		// when
		final ResponseEntity<Product> result = woocommerceService.deleteProduct(command);
		
		// then
		assertThat(result.getBody()).isNull();
		assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
	}
	
	@Test
	public void deleteProduct_whenSyncLineIsMissing_returnEmptyOKResponse() {
		// given
		final DeleteProductCommand command = new DeleteProductCommand(companyId, 22);
		
		// when
		final ResponseEntity<Product> result = woocommerceService.deleteProduct(command);
		
		// then
		assertThat(result.getBody()).isNull();
		assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
	}
}
