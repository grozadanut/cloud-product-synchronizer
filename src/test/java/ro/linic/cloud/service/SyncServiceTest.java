package ro.linic.cloud.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.endsWith;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.text.MessageFormat;
import java.util.List;
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
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.hateoas.CollectionModel;
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
public class SyncServiceTest {

	@Mock private WoocommerceApi woocommerceApi;
	@Mock private SyncConnectionRepository syncConnRepo;
	@Mock private SyncProductRepository syncProductRepo;
	@Mock private RestTemplate restTemplate;
	@Mock private Traverson traversonMock;
	@Mock private TraversalBuilder traversonBuilderMock;
	@Spy @InjectMocks private SyncServiceImpl syncService;
	
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
	public void givenCommandIsValid_whenCreateProductCalled_thenCreateSyncLineAndNotify() {
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
		final ResponseEntity<Product> result = syncService.createProduct(command);
		
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
		
		final ArgumentCaptor<HttpEntity<CreateNotificationCommand>> notifCommandCaptor = ArgumentCaptor.forClass(HttpEntity.class);
		verify(restTemplate).exchange(endsWith("/notification"), eq(HttpMethod.POST),
				notifCommandCaptor.capture(), eq(Void.class));
		final HttpEntity<CreateNotificationCommand> notifCommand = notifCommandCaptor.getValue();
		assertThat(notifCommand.getBody().getNotification()).isEqualTo(
				"Produs adaugat pe woocommerce" + System.lineSeparator()
				+ "ID: 55" + System.lineSeparator()
				+ "Cod: 59" + System.lineSeparator()
				+ "Nume: cement 40kg" + System.lineSeparator()
				+ "Pret: 29.5" + System.lineSeparator()
				+ "Acceseaza http://linic.ro pentru a edita");
	}
	
	@Test
	public void givenCommandIsNull_whenCreateProductCalled_thenThrowNPE() {
		assertThrows(NullPointerException.class, () -> syncService.createProduct(null));
	}
	
	@Test
	public void givenWooReturnsErrorCode_whenCreateProductCalled_thenThrowException() {
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
		// then
		assertThatThrownBy(() -> syncService.createProduct(command))
		.isInstanceOf(RuntimeException.class)
		.hasMessage("Woocommerce status code "+HttpStatus.BAD_REQUEST);
	}
	
	@Test
	public void givenSyncConnectionIsMissing_whenCreateProductCalled_thenReturnEmptyOKResponse() {
		// given
		final CreateProductCommand command = new CreateProductCommand(2, 22, "59", "cement 40kg", "buc", new BigDecimal("29.5"));
		
		// when
		final ResponseEntity<Product> result = syncService.createProduct(command);
		
		// then
		assertThat(result.getBody()).isNull();
		assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
	}
	
	@Test
	public void givenSyncLineIsPresent_whenCreateProductCalled_thenThrowException() {
		// given
		final CreateProductCommand command = new CreateProductCommand(companyId, 22, "59", "cement 40kg", "buc", new BigDecimal("29.5"));
		
		when(syncProductRepo.findBySyncConnectionIdAndProductId(syncConnection.getId(), command.getProductId()))
		.thenReturn(Optional.of(new SyncLine(1L, syncConnection, command.getProductId(), 1, null)));
		
		// when
		// then
		assertThatThrownBy(() -> syncService.createProduct(command))
		.isInstanceOf(RuntimeException.class);
		
	}
	
	@Test
	public void givenCommandOptionalFieldsAreNull_whenCreateProductCalled_thenCreateProductWithNullFields() {
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
		final ResponseEntity<Product> result = syncService.createProduct(command);
		
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
		
		final ArgumentCaptor<HttpEntity<CreateNotificationCommand>> notifCommandCaptor = ArgumentCaptor.forClass(HttpEntity.class);
		verify(restTemplate).exchange(endsWith("/notification"), eq(HttpMethod.POST),
				notifCommandCaptor.capture(), eq(Void.class));
		final HttpEntity<CreateNotificationCommand> notifCommand = notifCommandCaptor.getValue();
		assertThat(notifCommand.getBody().getNotification()).isEqualTo(
				"Produs adaugat pe woocommerce" + System.lineSeparator()
				+ "ID: 55" + System.lineSeparator()
				+ "Cod: null" + System.lineSeparator()
				+ "Nume: null" + System.lineSeparator()
				+ "Pret: null" + System.lineSeparator()
				+ "Acceseaza http://linic.ro pentru a edita");
	}
	
	@Test
	public void givenCommandIsValid_whenUpdatePriceCalled_thenUpdateWooPrice() {
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
		final ResponseEntity<Product> result = syncService.updatePrice(command);
		
		// then
		assertThat(result.getBody().getId()).isEqualTo(wooProd.getId());
		assertThat(result.getBody().getPricePerUom()).isEqualByComparingTo(wooProd.getPricePerUom());
	}
	
	@Test
	public void givenSyncConnectionIsMissing_whenUpdatePriceCalled_thenReturnEmptyOKResponse() {
		// given
		final ChangePriceCommand command = new ChangePriceCommand(2, 22, new BigDecimal("31"));
		
		// when
		final ResponseEntity<Product> result = syncService.updatePrice(command);
		
		// then
		assertThat(result.getBody()).isNull();
		assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
	}
	
	@Test
	public void givenSyncLineIsMissing_whenUpdatePriceCalled_thenReturnEmptyOKResponse() {
		// given
		final ChangePriceCommand command = new ChangePriceCommand(companyId, 22, new BigDecimal("31"));
		
		// when
		final ResponseEntity<Product> result = syncService.updatePrice(command);
		
		// then
		assertThat(result.getBody()).isNull();
		assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
	}
	
	@Test
	public void givenPriceIsNull_whenUpdatePriceCalled_thenSetWooPriceToNull() {
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
		final ResponseEntity<Product> result = syncService.updatePrice(command);
		
		// then
		assertThat(result.getBody().getId()).isEqualTo(wooProd.getId());
		assertThat(result.getBody().getPricePerUom()).isNull();
	}
	
	@Test
	public void givenCommandIsValid_whenUpdateStockCalled_thenUpdateWooStock() {
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
		final ResponseEntity<Product> result = syncService.updateStock(command);
		
		// then
		assertThat(result.getBody().getId()).isEqualTo(wooProd.getId());
		assertThat(result.getBody().getStock()).isEqualByComparingTo(wooProd.getStock());
	}
	
	@Test
	public void givenSyncConnectionIsMissing_whenUpdateStockCalled_thenReturnEmptyOKResponse() {
		// given
		final ChangeStockCommand command = new ChangeStockCommand(2, 22, new BigDecimal("650.5"));
		
		// when
		final ResponseEntity<Product> result = syncService.updateStock(command);
		
		// then
		assertThat(result.getBody()).isNull();
		assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
	}
	
	@Test
	public void givenSyncLineIsMissing_whenUpdateStockCalled_thenReturnEmptyOKResponse() {
		// given
		final ChangeStockCommand command = new ChangeStockCommand(companyId, 22, new BigDecimal("650.5"));
		
		// when
		final ResponseEntity<Product> result = syncService.updateStock(command);
		
		// then
		assertThat(result.getBody()).isNull();
		assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
	}
	
	@Test
	public void givenStockIsNull_whenUpdateStockCalled_thenSetWooStockToNull() {
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
		final ResponseEntity<Product> result = syncService.updateStock(command);
		
		// then
		assertThat(result.getBody().getId()).isEqualTo(wooProd.getId());
		assertThat(result.getBody().getStock()).isNull();
	}
	
	@Test
	public void givenCommandIsValid_whenUpdateNameCalled_thenNotify() {
		// given
		final ChangeNameCommand command = new ChangeNameCommand(companyId, 22, "59", "cement holcim 40kg");
		final int wooId = 55;
		
		when(syncProductRepo.findBySyncConnectionIdAndProductId(syncConnection.getId(), command.getProductId()))
		.thenReturn(Optional.of(new SyncLine(1L, syncConnection, command.getProductId(), wooId, null)));
		
		// when
		syncService.updateName(command);
		
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
	public void givenSyncConnectionIsMissing_whenUpdateNameCalled_thenReturnEmptyOKResponse() {
		// given
		final ChangeNameCommand command = new ChangeNameCommand(2, 22, "59", "cement holcim 40kg");
		
		// when
		final ResponseEntity<Product> result = syncService.updateName(command);
		
		// then
		assertThat(result.getBody()).isNull();
		assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
	}
	
	@Test
	public void givenSyncLineIsMissing_whenUpdateNameCalled_thenReturnEmptyOKResponse() {
		// given
		final ChangeNameCommand command = new ChangeNameCommand(companyId, 22, "59", "cement holcim 40kg");
		
		// when
		final ResponseEntity<Product> result = syncService.updateName(command);
		
		// then
		assertThat(result.getBody()).isNull();
		assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
	}
	
	@Test
	public void givenNameIsNull_whenUpdateNameCalled_thenNotifyWithEmptyName() {
		// given
		final ChangeNameCommand command = new ChangeNameCommand(companyId, 22, "59", null);
		final int wooId = 55;
		
		when(syncProductRepo.findBySyncConnectionIdAndProductId(syncConnection.getId(), command.getProductId()))
		.thenReturn(Optional.of(new SyncLine(1L, syncConnection, command.getProductId(), wooId, null)));
		
		// when
		syncService.updateName(command);
		
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
	public void givenCommandIsValid_whenDeleteProductCalled_thenDeleteSyncLineAndDeactivateWooProduct() {
		// given
		final DeleteProductCommand command = new DeleteProductCommand(companyId, 22);
		final int wooId = 55;
		
		when(syncProductRepo.findBySyncConnectionIdAndProductId(syncConnection.getId(), command.getProductId()))
		.thenReturn(Optional.of(new SyncLine(1L, syncConnection, command.getProductId(), wooId, null)));
		
		// when
		syncService.deleteProduct(command);
		
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
	public void givenSyncConnectionIsMissing_whenDeleteProductCalled_thenReturnEmptyOKResponse() {
		// given
		final DeleteProductCommand command = new DeleteProductCommand(2, 22);
		
		// when
		final ResponseEntity<Product> result = syncService.deleteProduct(command);
		
		// then
		assertThat(result.getBody()).isNull();
		assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
	}
	
	@Test
	public void givenSyncLineIsMissing_whenDeleteProductCalled_thenReturnEmptyOKResponse() {
		// given
		final DeleteProductCommand command = new DeleteProductCommand(companyId, 22);
		
		// when
		final ResponseEntity<Product> result = syncService.deleteProduct(command);
		
		// then
		assertThat(result.getBody()).isNull();
		assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
	}
	
	@Test
	public void givenProductExistsInWooAndLinic_whenCreateConnection_thenCreateSyncLineAndUpdateWoo() {
		// given
		final SyncConnection syncConnection = this.syncConnection.withCompanyId(2);
		final Product linicProd = Product.builder()
				.id(22)
				.barcode("59")
				.name("cement 40kg")
				.uom("buc")
				.pricePerUom(new BigDecimal("29.5"))
				.stock(new BigDecimal("600"))
				.build();
		final Product wooProd = Product.builder()
				.id(55)
				.barcode("59")
				.name("cement 40kg")
				.uom("buc")
				.pricePerUom(new BigDecimal("27"))
				.stock(new BigDecimal("0"))
				.build();
		
		when(woocommerceApi.allProducts(syncConnection))
		.thenReturn(List.of(wooProd));
		
		doReturn(traversonMock).when(syncService).createTraverson(any());
		when(traversonMock.follow("products", "search", "findByCompanyIdAndBarcode")).thenReturn(traversonBuilderMock);
		when(traversonBuilderMock.withTemplateParameters(any())).thenReturn(traversonBuilderMock);
		when(traversonBuilderMock.toObject(new ParameterizedTypeReference<CollectionModel<Product>>(){}))
		.thenReturn(CollectionModel.of(List.of(linicProd)));
		
		// when
		final String result = syncService.createConnection(syncConnection);
		
		// then
		assertThat(result).isEqualTo(MessageFormat.format("{1}Set price of 59 cement 40kg from 27 to 29.5{1}"
				+ "Set stock of 59 cement 40kg from 0 to 600{1}"
				+ "{1}"
				+ "Woocommerce products deactivated 0/{0}{1}"
				+ "Name differences 0/{0}{1}"
				+ "Woocommerce price updated 1/{0}{1}"
				+ "Woocommerce stock updated 1/{0}{1}",
				1, System.lineSeparator()));
		
		final ArgumentCaptor<SyncLine> syncLineCaptor = ArgumentCaptor.forClass(SyncLine.class);
		verify(syncProductRepo).save(syncLineCaptor.capture());
		final SyncLine savedSyncLine = syncLineCaptor.getValue();
		assertThat(savedSyncLine.getProductId()).isEqualTo(linicProd.getId());
		assertThat(savedSyncLine.getWooId()).isEqualTo(wooProd.getId());
		assertThat(savedSyncLine.getWooName()).isNull();
		assertThat(savedSyncLine.getSyncConnection()).isEqualTo(syncConnection);
		
		final ArgumentCaptor<Product> wooProductCaptor = ArgumentCaptor.forClass(Product.class);
		verify(woocommerceApi).putProduct(eq(syncConnection), wooProductCaptor.capture());
		final Product udpatedWooProduct = wooProductCaptor.getValue();
		assertThat(udpatedWooProduct.getId()).isEqualTo(wooProd.getId());
		assertThat(udpatedWooProduct.getName()).isEqualTo(wooProd.getName());
		assertThat(udpatedWooProduct.getBarcode()).isEqualTo(linicProd.getBarcode());
		assertThat(udpatedWooProduct.getPricePerUom()).isEqualByComparingTo(linicProd.getPricePerUom());
		assertThat(udpatedWooProduct.getStock()).isEqualByComparingTo(linicProd.getStock());
		assertThat(udpatedWooProduct.getUom()).isEqualTo(linicProd.getUom());
	}
	
	@Test
	public void givenProductExistsInWooNotLinic_whenCreateConnection_thenDeactivateWooProduct() {
		// given
		final SyncConnection syncConnection = this.syncConnection.withCompanyId(2);
		final Product wooProd = Product.builder()
				.id(55)
				.barcode("59")
				.name("cement 40kg")
				.uom("buc")
				.pricePerUom(new BigDecimal("27"))
				.stock(new BigDecimal("0"))
				.visible(true)
				.build();
		
		when(woocommerceApi.allProducts(syncConnection))
		.thenReturn(List.of(wooProd));
		
		doReturn(traversonMock).when(syncService).createTraverson(any());
		when(traversonMock.follow("products", "search", "findByCompanyIdAndBarcode")).thenReturn(traversonBuilderMock);
		when(traversonMock.follow("products", "search", "findByCompanyIdAndNameIgnoreCase")).thenReturn(traversonBuilderMock);
		when(traversonBuilderMock.withTemplateParameters(any())).thenReturn(traversonBuilderMock);
		when(traversonBuilderMock.toObject(new ParameterizedTypeReference<CollectionModel<Product>>(){}))
		.thenReturn(CollectionModel.of(List.of()));
		
		// when
		final String result = syncService.createConnection(syncConnection);
		
		// then
		assertThat(result).isEqualTo(MessageFormat.format("{1}Deactivating Woo SKU 59: cement 40kg{1}"
				+ "{1}"
				+ "Woocommerce products deactivated 1/{0}{1}"
				+ "Name differences 0/{0}{1}"
				+ "Woocommerce price updated 0/{0}{1}"
				+ "Woocommerce stock updated 0/{0}{1}",
				1, System.lineSeparator()));
		
		final ArgumentCaptor<Integer> wooIdCaptor = ArgumentCaptor.forClass(Integer.class);
		verify(woocommerceApi).deactivateProduct(eq(syncConnection), wooIdCaptor.capture());
		final Integer deactivatedWooId = wooIdCaptor.getValue();
		assertThat(deactivatedWooId).isEqualTo(wooProd.getId());
	}
	
	@Test
	public void givenProductExistsInLinicNotWoo_whenCreateConnection_thenDontCreateSyncLine() {
		// given
		final SyncConnection syncConnection = this.syncConnection.withCompanyId(2);
		final Product linicProd = Product.builder()
				.id(22)
				.barcode("59")
				.name("cement 40kg")
				.uom("buc")
				.pricePerUom(new BigDecimal("29.5"))
				.stock(new BigDecimal("600"))
				.build();
		
		when(woocommerceApi.allProducts(syncConnection))
		.thenReturn(List.of());
		
		doReturn(traversonMock).when(syncService).createTraverson(any());
		lenient().when(traversonMock.follow("products", "search", "findByCompanyIdAndBarcode")).thenReturn(traversonBuilderMock);
		lenient().when(traversonMock.follow("products", "search", "findByCompanyIdAndNameIgnoreCase")).thenReturn(traversonBuilderMock);
		lenient().when(traversonBuilderMock.withTemplateParameters(any())).thenReturn(traversonBuilderMock);
		lenient().when(traversonBuilderMock.toObject(new ParameterizedTypeReference<CollectionModel<Product>>(){}))
		.thenReturn(CollectionModel.of(List.of(linicProd)));
		
		// when
		final String result = syncService.createConnection(syncConnection);
		
		// then
		assertThat(result).isEqualTo(MessageFormat.format("{1}{1}"
				+ "Woocommerce products deactivated 0/{0}{1}"
				+ "Name differences 0/{0}{1}"
				+ "Woocommerce price updated 0/{0}{1}"
				+ "Woocommerce stock updated 0/{0}{1}",
				0, System.lineSeparator()));
		
		verifyNoInteractions(syncProductRepo);
		verify(woocommerceApi, never()).putProduct(eq(syncConnection), any());
	}
	
	@Test
	public void givenDifferentWooName_whenCreateConnection_thenCreateSyncLineWithWooName() {
		// given
		final SyncConnection syncConnection = this.syncConnection.withCompanyId(2);
		final Product linicProd = Product.builder()
				.id(22)
				.barcode("59")
				.name("cement holcim 40kg")
				.uom("buc")
				.pricePerUom(new BigDecimal("29.5"))
				.stock(new BigDecimal("600"))
				.build();
		final Product wooProd = Product.builder()
				.id(55)
				.barcode("59")
				.name("cement 40kg")
				.uom("buc")
				.pricePerUom(new BigDecimal("27"))
				.stock(new BigDecimal("0"))
				.build();
		
		when(woocommerceApi.allProducts(syncConnection))
		.thenReturn(List.of(wooProd));
		
		doReturn(traversonMock).when(syncService).createTraverson(any());
		when(traversonMock.follow("products", "search", "findByCompanyIdAndBarcode")).thenReturn(traversonBuilderMock);
		when(traversonBuilderMock.withTemplateParameters(any())).thenReturn(traversonBuilderMock);
		when(traversonBuilderMock.toObject(new ParameterizedTypeReference<CollectionModel<Product>>(){}))
		.thenReturn(CollectionModel.of(List.of(linicProd)));
		
		// when
		final String result = syncService.createConnection(syncConnection);
		
		// then
		assertThat(result).isEqualTo(MessageFormat.format("{1}59 Woo name(cement 40kg) != cement holcim 40kg{1}"
				+ "Set price of 59 cement 40kg from 27 to 29.5{1}"
				+ "Set stock of 59 cement 40kg from 0 to 600{1}"
				+ "{1}"
				+ "Woocommerce products deactivated 0/{0}{1}"
				+ "Name differences 1/{0}{1}"
				+ "Woocommerce price updated 1/{0}{1}"
				+ "Woocommerce stock updated 1/{0}{1}",
				1, System.lineSeparator()));
		
		final ArgumentCaptor<SyncLine> syncLineCaptor = ArgumentCaptor.forClass(SyncLine.class);
		verify(syncProductRepo).save(syncLineCaptor.capture());
		final SyncLine savedSyncLine = syncLineCaptor.getValue();
		assertThat(savedSyncLine.getProductId()).isEqualTo(linicProd.getId());
		assertThat(savedSyncLine.getWooId()).isEqualTo(wooProd.getId());
		assertThat(savedSyncLine.getWooName()).isEqualTo(wooProd.getName());
		assertThat(savedSyncLine.getSyncConnection()).isEqualTo(syncConnection);
		
		final ArgumentCaptor<Product> wooProductCaptor = ArgumentCaptor.forClass(Product.class);
		verify(woocommerceApi).putProduct(eq(syncConnection), wooProductCaptor.capture());
		final Product udpatedWooProduct = wooProductCaptor.getValue();
		assertThat(udpatedWooProduct.getId()).isEqualTo(wooProd.getId());
		assertThat(udpatedWooProduct.getName()).isEqualTo(wooProd.getName());
		assertThat(udpatedWooProduct.getBarcode()).isEqualTo(linicProd.getBarcode());
		assertThat(udpatedWooProduct.getPricePerUom()).isEqualByComparingTo(linicProd.getPricePerUom());
		assertThat(udpatedWooProduct.getStock()).isEqualByComparingTo(linicProd.getStock());
		assertThat(udpatedWooProduct.getUom()).isEqualTo(linicProd.getUom());
	}
	
	@Test
	public void givenDifferentWooBarcode_whenCreateConnection_thenFindLinicProductByName() {
		// given
		final SyncConnection syncConnection = this.syncConnection.withCompanyId(2);
		final Product linicProd = Product.builder()
				.id(22)
				.barcode("59")
				.name("cement 40kg")
				.uom("buc")
				.pricePerUom(new BigDecimal("29.5"))
				.stock(new BigDecimal("600"))
				.build();
		final Product wooProd = Product.builder()
				.id(55)
				.barcode("599")
				.name("cement 40kg")
				.uom("buc")
				.pricePerUom(new BigDecimal("27"))
				.stock(new BigDecimal("0"))
				.build();
		
		when(woocommerceApi.allProducts(syncConnection))
		.thenReturn(List.of(wooProd));
		
		doReturn(traversonMock).when(syncService).createTraverson(any());
		when(traversonMock.follow("products", "search", "findByCompanyIdAndBarcode")).thenReturn(traversonBuilderMock);
		when(traversonMock.follow("products", "search", "findByCompanyIdAndNameIgnoreCase")).thenReturn(traversonBuilderMock);
		when(traversonBuilderMock.withTemplateParameters(any())).thenReturn(traversonBuilderMock);
		when(traversonBuilderMock.toObject(new ParameterizedTypeReference<CollectionModel<Product>>(){}))
		.thenReturn(CollectionModel.of(List.of()), CollectionModel.of(List.of(linicProd)));
		
		// when
		final String result = syncService.createConnection(syncConnection);
		
		// then
		assertThat(result).isEqualTo(MessageFormat.format("{1}Set price of 59 cement 40kg from 27 to 29.5{1}"
				+ "Set stock of 59 cement 40kg from 0 to 600{1}"
				+ "{1}"
				+ "Woocommerce products deactivated 0/{0}{1}"
				+ "Name differences 0/{0}{1}"
				+ "Woocommerce price updated 1/{0}{1}"
				+ "Woocommerce stock updated 1/{0}{1}",
				1, System.lineSeparator()));
		
		final ArgumentCaptor<SyncLine> syncLineCaptor = ArgumentCaptor.forClass(SyncLine.class);
		verify(syncProductRepo).save(syncLineCaptor.capture());
		final SyncLine savedSyncLine = syncLineCaptor.getValue();
		assertThat(savedSyncLine.getProductId()).isEqualTo(linicProd.getId());
		assertThat(savedSyncLine.getWooId()).isEqualTo(wooProd.getId());
		assertThat(savedSyncLine.getWooName()).isNull();
		assertThat(savedSyncLine.getSyncConnection()).isEqualTo(syncConnection);
		
		final ArgumentCaptor<Product> wooProductCaptor = ArgumentCaptor.forClass(Product.class);
		verify(woocommerceApi).putProduct(eq(syncConnection), wooProductCaptor.capture());
		final Product udpatedWooProduct = wooProductCaptor.getValue();
		assertThat(udpatedWooProduct.getId()).isEqualTo(wooProd.getId());
		assertThat(udpatedWooProduct.getName()).isEqualTo(wooProd.getName());
		assertThat(udpatedWooProduct.getBarcode()).isEqualTo(linicProd.getBarcode());
		assertThat(udpatedWooProduct.getPricePerUom()).isEqualByComparingTo(linicProd.getPricePerUom());
		assertThat(udpatedWooProduct.getStock()).isEqualByComparingTo(linicProd.getStock());
		assertThat(udpatedWooProduct.getUom()).isEqualTo(linicProd.getUom());
	}
	
	@Test
	public void givenSyncConnWithCompanyIdExists_whenCreateConnection_thenThrowException() {
		assertThatThrownBy(() -> syncService.createConnection(syncConnection))
		.isInstanceOf(RuntimeException.class)
		.hasMessage("SyncConnection already exists for company id: 1");
	}
}
