package ro.linic.cloud.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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

import ro.linic.cloud.entity.SyncConnection;
import ro.linic.cloud.entity.SyncLine;
import ro.linic.cloud.pojo.Product;
import ro.linic.cloud.repository.SyncConnectionRepository;
import ro.linic.cloud.repository.SyncProductRepository;

@ExtendWith(MockitoExtension.class)
public class SyncServiceTest {

	@Mock private WoocommerceService woocommerceService;
	@Mock private SyncConnectionRepository syncConnRepo;
	@Mock private SyncProductRepository syncProductRepo;
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
	public void createConnection_whenProductExistsInWooAndLinic_createSyncLineAndUpdateWoo() {
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
		
		when(woocommerceService.allProducts(syncConnection))
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
		verify(woocommerceService).putProduct(eq(syncConnection), wooProductCaptor.capture());
		final Product udpatedWooProduct = wooProductCaptor.getValue();
		assertThat(udpatedWooProduct.getId()).isEqualTo(wooProd.getId());
		assertThat(udpatedWooProduct.getName()).isEqualTo(wooProd.getName());
		assertThat(udpatedWooProduct.getBarcode()).isEqualTo(linicProd.getBarcode());
		assertThat(udpatedWooProduct.getPricePerUom()).isEqualByComparingTo(linicProd.getPricePerUom());
		assertThat(udpatedWooProduct.getStock()).isEqualByComparingTo(linicProd.getStock());
		assertThat(udpatedWooProduct.getUom()).isEqualTo(linicProd.getUom());
	}
	
	@Test
	public void createConnection_whenProductExistsInWooNotLinic_deactivateWooProduct() {
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
		
		when(woocommerceService.allProducts(syncConnection))
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
		verify(woocommerceService).deactivateProduct(eq(syncConnection), wooIdCaptor.capture());
		final Integer deactivatedWooId = wooIdCaptor.getValue();
		assertThat(deactivatedWooId).isEqualTo(wooProd.getId());
	}
	
	@Test
	public void createConnection_whenProductExistsInLinicNotWoo_dontCreateSyncLine() {
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
		
		when(woocommerceService.allProducts(syncConnection))
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
		verify(woocommerceService, never()).putProduct(eq(syncConnection), any());
	}
	
	@Test
	public void createConnection_whenDifferentWooName_createSyncLineWithWooName() {
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
		
		when(woocommerceService.allProducts(syncConnection))
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
		verify(woocommerceService).putProduct(eq(syncConnection), wooProductCaptor.capture());
		final Product udpatedWooProduct = wooProductCaptor.getValue();
		assertThat(udpatedWooProduct.getId()).isEqualTo(wooProd.getId());
		assertThat(udpatedWooProduct.getName()).isEqualTo(wooProd.getName());
		assertThat(udpatedWooProduct.getBarcode()).isEqualTo(linicProd.getBarcode());
		assertThat(udpatedWooProduct.getPricePerUom()).isEqualByComparingTo(linicProd.getPricePerUom());
		assertThat(udpatedWooProduct.getStock()).isEqualByComparingTo(linicProd.getStock());
		assertThat(udpatedWooProduct.getUom()).isEqualTo(linicProd.getUom());
	}
	
	@Test
	public void createConnection_whenDifferentWooBarcode_findLinicProductByName() {
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
		
		when(woocommerceService.allProducts(syncConnection))
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
		verify(woocommerceService).putProduct(eq(syncConnection), wooProductCaptor.capture());
		final Product udpatedWooProduct = wooProductCaptor.getValue();
		assertThat(udpatedWooProduct.getId()).isEqualTo(wooProd.getId());
		assertThat(udpatedWooProduct.getName()).isEqualTo(wooProd.getName());
		assertThat(udpatedWooProduct.getBarcode()).isEqualTo(linicProd.getBarcode());
		assertThat(udpatedWooProduct.getPricePerUom()).isEqualByComparingTo(linicProd.getPricePerUom());
		assertThat(udpatedWooProduct.getStock()).isEqualByComparingTo(linicProd.getStock());
		assertThat(udpatedWooProduct.getUom()).isEqualTo(linicProd.getUom());
	}
	
	@Test
	public void createConnection_whenSyncConnWithCompanyIdExists_throwException() {
		assertThatThrownBy(() -> syncService.createConnection(syncConnection))
		.isInstanceOf(RuntimeException.class)
		.hasMessage("SyncConnection already exists for company id: 1");
	}
}
