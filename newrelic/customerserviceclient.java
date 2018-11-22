package com.nike.sales.subscriber.data.temp;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.nike.manage.domain.CustomerAccountReference;
import com.nike.manage.domain.GlobalSalesCustomerExtensionReference;
import com.nike.manage.domain.user.Customer;
import com.nike.kona.storeboard.v10.model.GlobalSalesCustomerExtension;
import com.nike.sales.manage.activator.CustomerSettingsActivator;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.HttpClients;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.util.CollectionUtils;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import java.util.List;
import java.util.Set;

/**
 * Created by ctay16 on 4/11/2014.
 */
public class CustomerServiceClient
{
  @Value("${customerService.url}")
  private String customerServiceUrl;
  @Value("${customerService.user}")
  private String customerServiceUser;
  @Value("${customerService.password}")
  private String customerServicePassword;

  private RestTemplate secureRestTemplate;

  public static final boolean DEFAULT_DISPLAY_BRAZIL_NOTA_FISCAL_INDICATOR = false;
  public static final boolean DEFAULT_DISPLAY_NET_PRICE_INDICATOR = true;
  public static final boolean DEFAULT_DISPLAY_RETAIL_PRICE_INDICATOR = true;
  public static final boolean DEFAULT_DISPLAY_WHOLESALE_PRICE_INDICATOR = true;
  public static final boolean DEFAULT_REDUCED_QUANTITY_ALERT_INDICATOR = false;
  public static final boolean DEFAULT_SHIPMENT_ALERT_INDICATOR = false;
  private static final String AUTH_SCOPE_HOST = null;
  private static final Integer AUTH_SCOPE_PORT = -1;

  public Customer getCustomerAccount(String accountNumber, String salesOrg)
  {
    StringBuilder params = new StringBuilder(256);

    params.append(customerServiceUrl).append("/ds-rest/customerAccount/").append(accountNumber).append("?metadata=globalSalesCustomerExtension");

    CustomerAccountReference customerAccount = secureRestTemplate.getForObject(params.toString(), CustomerAccountReference.class);

    return createCustomer(customerAccount, salesOrg);
  }

  public Customer getCustomerAccount(String accountNumber)
  {
    return getCustomerAccount(accountNumber, null);
  }

  private Customer createCustomer(CustomerAccountReference customerAccount, String salesOrg)
  {
    Customer customer = new Customer(customerAccount.getCustomerNumber(), customerAccount.getCustomerName());

    customer.setCountry(customerAccount.getContactInfo().get(0).getCountryCode());
    customer.setRegion(Integer.parseInt(customerAccount.getSalesOrgDivision().get(0).getSoldToRegionCode()));
    customer.setSalesOrgCd(customerAccount.getSalesOrgDivision().get(0).getSalesOrganizationCode());
    customer.setGlobalSalesCustomerExtension(findGlobalSalesCustomerExtension(customerAccount, salesOrg));

    return customer;
  }

  private GlobalSalesCustomerExtension findGlobalSalesCustomerExtension(CustomerAccountReference customerAccountReference, String salesOrg)
  {
    Set<GlobalSalesCustomerExtensionReference> globalSalesCustomerExtensionSet = customerAccountReference.getGlobalSalesCustomerExtension();
    GlobalSalesCustomerExtension retGlobalSalesCustomerExtension = initGlobalSalesCustomerExtension();

    if (!CollectionUtils.isEmpty(globalSalesCustomerExtensionSet))
    {
      GlobalSalesCustomerExtension sourceCustomerExtension;

      if (StringUtils.isBlank(salesOrg))
      {
        sourceCustomerExtension = globalSalesCustomerExtensionSet.iterator().next();
      }
      else
      {
        sourceCustomerExtension = matchCustomerExtensionBySalesOrg(globalSalesCustomerExtensionSet, salesOrg);
      }

      updateGlobalSalesCustomerExtension(retGlobalSalesCustomerExtension, sourceCustomerExtension);
    }

    return retGlobalSalesCustomerExtension;
  }

  private void updateGlobalSalesCustomerExtension(GlobalSalesCustomerExtension retGlobalSalesCustomerExtension,
                                                  GlobalSalesCustomerExtension globalSalesCustomerExtension)
  {
    if (globalSalesCustomerExtension != null)
    {
      retGlobalSalesCustomerExtension.setDisplayWholesalePriceIndicator(isDisplayWholesalePriceIndicator(globalSalesCustomerExtension));
      retGlobalSalesCustomerExtension.setDisplayRetailPriceIndicator(isDisplayRetailPriceIndicator(globalSalesCustomerExtension));
      retGlobalSalesCustomerExtension.setDisplayNetPriceIndicator(globalSalesCustomerExtension.isDisplayNetPriceIndicator());
      retGlobalSalesCustomerExtension.setDisplayBrazilNotaFiscalIndicator(globalSalesCustomerExtension.isDisplayBrazilNotaFiscalIndicator());
      retGlobalSalesCustomerExtension.setOrderReducedQuantityAlertIndicator(globalSalesCustomerExtension.isOrderReducedQuantityAlertIndicator());
      retGlobalSalesCustomerExtension.setShipmentAlertIndicator(globalSalesCustomerExtension.isShipmentAlertIndicator());
    }
  }

  /**
   * create a GlobalSalesCustomerExtension object with default values.
   *
   * @return  a new GlobalSalesCustomerExtension object
   */
  private GlobalSalesCustomerExtension initGlobalSalesCustomerExtension()
  {
    GlobalSalesCustomerExtension globalSalesCustomerExtension = new GlobalSalesCustomerExtension();

    globalSalesCustomerExtension.setDisplayBrazilNotaFiscalIndicator(DEFAULT_DISPLAY_BRAZIL_NOTA_FISCAL_INDICATOR);
    globalSalesCustomerExtension.setDisplayNetPriceIndicator(DEFAULT_DISPLAY_NET_PRICE_INDICATOR);
    globalSalesCustomerExtension.setDisplayRetailPriceIndicator(DEFAULT_DISPLAY_RETAIL_PRICE_INDICATOR);
    globalSalesCustomerExtension.setDisplayWholesalePriceIndicator(DEFAULT_DISPLAY_WHOLESALE_PRICE_INDICATOR);
    globalSalesCustomerExtension.setOrderReducedQuantityAlertIndicator(DEFAULT_REDUCED_QUANTITY_ALERT_INDICATOR);
    globalSalesCustomerExtension.setShipmentAlertIndicator(DEFAULT_SHIPMENT_ALERT_INDICATOR);

    return globalSalesCustomerExtension;
  }

  private GlobalSalesCustomerExtension matchCustomerExtensionBySalesOrg(Set<GlobalSalesCustomerExtensionReference> extensionSet, String salesOrg)
  {
    GlobalSalesCustomerExtension retExtension = null;

    for (GlobalSalesCustomerExtension globalSalesCustomerExtension : extensionSet)
    {
      String salesOrganizationCode = globalSalesCustomerExtension.getSalesOrganizationCode();

      if (StringUtils.equals(salesOrg, salesOrganizationCode))
      {
        retExtension = globalSalesCustomerExtension;

        break;
      }
    }

    return retExtension;
  }

  @PostConstruct
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  // This is a PostConstruct method, it is only called by Spring, Sonar flagged it as unused so the SuppressWarning is necessary.
  // PostConstruct is necessary here because the RestTemplate can only be created after the @Value fields (url, username, password) are set.
  // Initially an attempt was made to set the RestTemplate in the Constructor, but that fails because when Spring calls the Constructor
  // the @Value fields were still nulls (They are only set when all the beans were created and wired up by Spring).
  private void setSecureRestTemplateAfterSpringDependencies()
  {
    secureRestTemplate = createSecureRestTemplate();
  }

  private RestTemplate createSecureRestTemplate()
  {
    CredentialsProvider credsProvider = new BasicCredentialsProvider();

    credsProvider.setCredentials(new AuthScope(AUTH_SCOPE_HOST, AUTH_SCOPE_PORT), new UsernamePasswordCredentials(customerServiceUser, customerServicePassword));

    HttpClient httpClient = HttpClients.custom().setDefaultCredentialsProvider(credsProvider).build();
    HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory(httpClient);

    RestTemplate secureRestTemplate = new RestTemplate(factory);
    List<HttpMessageConverter<?>> converters = secureRestTemplate.getMessageConverters();

    for (HttpMessageConverter<?> converter : converters)
    {
      if (converter instanceof MappingJackson2HttpMessageConverter)
      {
        ((MappingJackson2HttpMessageConverter) converter).getObjectMapper().configure(
                DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
      }
    }

    return secureRestTemplate;
  }

  public Boolean isDisplayWholesalePriceIndicator(GlobalSalesCustomerExtension globalSalesCustomerExtension) {
    return globalSalesCustomerExtension.getDisplayWholesalePrice().contains("Shipment Alert");
  }

  public Boolean isDisplayRetailPriceIndicator(GlobalSalesCustomerExtension globalSalesCustomerExtension) {
    return globalSalesCustomerExtension.getDisplayRetailPrice().contains("Shipment Alert");
  }
}
