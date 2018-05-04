package tech.lapsa.epayment.facade.beans;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.Currency;
import java.util.Properties;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.ejb.EJB;
import javax.ejb.EJBException;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.inject.Inject;

import tech.lapsa.epayment.dao.BankDAO.BankDAORemote;
import tech.lapsa.epayment.dao.InvoiceDAO.InvoiceDAORemote;
import tech.lapsa.epayment.dao.PaymentDAO.PaymentDAORemote;
import tech.lapsa.epayment.dao.QazkomErrorDAO.QazkomErrorDAORemote;
import tech.lapsa.epayment.dao.QazkomOrderDAO.QazkomOrderDAORemote;
import tech.lapsa.epayment.dao.QazkomPaymentDAO.QazkomPaymentDAORemote;
import tech.lapsa.epayment.domain.Bank;
import tech.lapsa.epayment.domain.Invoice;
import tech.lapsa.epayment.domain.Invoice.InvoiceBuilder;
import tech.lapsa.epayment.domain.NonUniqueNumberException;
import tech.lapsa.epayment.domain.NumberOfAttemptsExceedException;
import tech.lapsa.epayment.domain.Payment;
import tech.lapsa.epayment.domain.QazkomError;
import tech.lapsa.epayment.domain.QazkomOrder;
import tech.lapsa.epayment.domain.QazkomPayment;
import tech.lapsa.epayment.domain.QazkomPayment.QazkomPaymentBuilder;
import tech.lapsa.epayment.domain.UnknownPayment;
import tech.lapsa.epayment.facade.EpaymentFacade;
import tech.lapsa.epayment.facade.EpaymentFacade.EpaymentFacadeLocal;
import tech.lapsa.epayment.facade.EpaymentFacade.EpaymentFacadeRemote;
import tech.lapsa.epayment.facade.InvoiceNotFound;
import tech.lapsa.epayment.facade.NotificationFacade.Notification;
import tech.lapsa.epayment.facade.NotificationFacade.Notification.NotificationChannel;
import tech.lapsa.epayment.facade.NotificationFacade.Notification.NotificationEventType;
import tech.lapsa.epayment.facade.NotificationFacade.Notification.NotificationRecipientType;
import tech.lapsa.epayment.facade.NotificationFacade.NotificationFacadeLocal;
import tech.lapsa.epayment.facade.PaymentMethod;
import tech.lapsa.epayment.facade.PaymentMethod.Http;
import tech.lapsa.epayment.shared.entity.InvoiceHasPaidJmsEvent;
import tech.lapsa.epayment.shared.jms.EpaymentDestinations;
import tech.lapsa.java.commons.exceptions.IllegalArgument;
import tech.lapsa.java.commons.exceptions.IllegalState;
import tech.lapsa.java.commons.function.MyExceptions;
import tech.lapsa.java.commons.function.MyMaps;
import tech.lapsa.java.commons.function.MyNumbers;
import tech.lapsa.java.commons.function.MyObjects;
import tech.lapsa.java.commons.function.MyOptionals;
import tech.lapsa.java.commons.function.MyStrings;
import tech.lapsa.java.commons.logging.MyLogger;
import tech.lapsa.lapsa.jmsRPC.client.JmsDestination;
import tech.lapsa.lapsa.jmsRPC.client.JmsEventNotificatorClient;
import tech.lapsa.patterns.dao.NotFound;

@Stateless(name = EpaymentFacadeBean.BEAN_NAME)
public class EpaymentFacadeBean implements EpaymentFacadeLocal, EpaymentFacadeRemote {

    static final String JNDI_CONFIG = "epayment/resource/Configuration";
    static final String PROPERTY_DEFAULT_PAYMENT_URI_PATTERN = "default-payment-uri.pattern";

    private QazkomSettings qazkomSettings;

    @Resource(lookup = QazkomSettings.JNDI_QAZKOM_CONFIG)
    private Properties qazkomConfig;

    @PostConstruct
    public void init() {
	qazkomSettings = new QazkomSettings(qazkomConfig);
    }

    // READERS

    @Override
    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    public URI getDefaultPaymentURI(final String invoiceNumber) throws IllegalArgument, InvoiceNotFound {
	try {
	    return _getDefaultPaymentURI(invoiceNumber);
	} catch (final IllegalArgumentException e) {
	    throw new IllegalArgument(e);
	}
    }

    @Override
    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    public Invoice getInvoiceByNumber(final String invoiceNumber) throws IllegalArgument, InvoiceNotFound {
	try {
	    return _invoiceByNumber(invoiceNumber);
	} catch (final IllegalArgumentException e) {
	    throw new IllegalArgument(e);
	}
    }

    @Override
    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    public boolean hasInvoiceWithNumber(final String invoiceNumber) throws IllegalArgument {
	try {
	    return _hasInvoiceWithNumber(invoiceNumber);
	} catch (final IllegalArgumentException e) {
	    throw new IllegalArgument(e);
	}
    }

    @Override
    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    public PaymentMethod qazkomHttpMethod(final URI postbackURI,
	    final URI failureURI,
	    final URI returnURI,
	    final Invoice forInvoice) throws IllegalArgument {
	try {
	    return _qazkomHttpMethod(postbackURI, failureURI, returnURI, forInvoice);
	} catch (final IllegalArgumentException e) {
	    throw new IllegalArgument(e);
	}
    }

    @Override
    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    public PaymentMethod qazkomHttpMethod(final URI postbackURI,
	    final URI failureURI,
	    final URI returnURI,
	    final String invoiceNumber) throws IllegalArgument, InvoiceNotFound {
	try {
	    return _qazkomHttpMethod(postbackURI, failureURI, returnURI, invoiceNumber);
	} catch (final IllegalArgumentException e) {
	    throw new IllegalArgument(e);
	}
    }

    // MODIFIERS

    @Override
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public Invoice invoiceAccept(final InvoiceBuilder builder) throws IllegalArgument {
	try {
	    return _invoiceAccept(builder);
	} catch (final IllegalArgumentException e) {
	    throw new IllegalArgument(e);
	}
    }

    @Override
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void completeWithUnknownPayment(final String invoiceNumber,
	    final Double paidAmount,
	    final Currency paidCurency,
	    final Instant paidInstant,
	    final String paidReference,
	    final String payerName) throws IllegalArgument, IllegalState, InvoiceNotFound {
	try {
	    _completeWithUnknownPayment(invoiceNumber, paidAmount, paidCurency, paidInstant, paidReference, payerName);
	} catch (final IllegalArgumentException e) {
	    throw new IllegalArgument(e);
	} catch (final IllegalStateException e) {
	    throw new IllegalState(e);
	}
    }

    @Override
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void expireInvoice(final String invoiceNumber) throws IllegalArgument, IllegalState, InvoiceNotFound {
	try {
	    _expireInvoice(invoiceNumber);
	} catch (final IllegalArgumentException e) {
	    throw new IllegalArgument(e);
	} catch (final IllegalStateException e) {
	    throw new IllegalState(e);
	}
    }

    @Override
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public String processQazkomFailure(final String failureXml) throws IllegalArgument {
	try {
	    return _processQazkomFailure(failureXml);
	} catch (final IllegalArgumentException e) {
	    throw new IllegalArgument(e);
	}
    }

    @Override
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void completeWithQazkomPayment(final String postbackXml) throws IllegalArgument, IllegalState {
	try {
	    _completeWithQazkomPayment(postbackXml);
	} catch (final IllegalArgumentException e) {
	    throw new IllegalArgument(e);
	} catch (final IllegalStateException e) {
	    throw new IllegalState(e);
	}
    }

    // PRIVATE

    private final MyLogger logger = MyLogger.newBuilder() //
	    .withNameOf(EpaymentFacade.class) //
	    .build();

    // dao (remote)

    @EJB
    private InvoiceDAORemote invoiceDAO;

    @EJB
    private PaymentDAORemote paymentDAO;

    @EJB
    private QazkomOrderDAORemote qoDAO;

    @EJB
    private QazkomPaymentDAORemote qpDAO;

    @EJB
    private QazkomErrorDAORemote qeDAO;

    // own (local)

    @EJB
    private NotificationFacadeLocal notifications;

    @Resource(lookup = JNDI_CONFIG)
    private Properties epaymentConfig;

    private boolean _hasInvoiceWithNumber(final String invoiceNumber) throws IllegalArgumentException {
	try {
	    _invoiceByNumber(invoiceNumber);
	    return true;
	} catch (final InvoiceNotFound e) {
	    return false;
	}
    }

    private Invoice _invoiceByNumber(final String invoiceNumber) throws IllegalArgumentException, InvoiceNotFound {
	MyStrings.requireNonEmpty(invoiceNumber, "invoiceNumber");
	try {
	    return invoiceDAO.getByNumber(invoiceNumber);
	} catch (final NotFound e) {
	    throw MyExceptions.format(InvoiceNotFound::new, "Invoice not found with number %1$s", invoiceNumber);
	} catch (final IllegalArgument e) {
	    // it should not happens
	    throw new EJBException(e.getMessage());
	}
    }

    private URI _getDefaultPaymentURI(final String invoiceNumber) throws IllegalArgumentException, InvoiceNotFound {
	final Invoice invoice = _invoiceByNumber(invoiceNumber);
	return _getDefaultPaymentURI(invoice);
    }

    private URI _getDefaultPaymentURI(final Invoice invoice) throws IllegalArgumentException {
	MyObjects.requireNonNull(invoice, "invoice");

	final String pattern = epaymentConfig.getProperty(PROPERTY_DEFAULT_PAYMENT_URI_PATTERN);
	try {
	    final String parsed = pattern //
		    .replace("@INVOICE_ID@", invoice.getNumber()) //
		    .replace("@INVOICE_NUMBER@", invoice.getNumber()) //
		    .replace("@LANG@", invoice.getConsumerPreferLanguage().getTag());
	    return new URI(parsed);
	} catch (final URISyntaxException | NullPointerException e) {
	    // it should not happens
	    throw new EJBException(e.getMessage());
	}
    }

    private Invoice _invoiceAccept(final InvoiceBuilder builder) throws IllegalArgumentException {
	MyObjects.requireNonNull(builder, "builder");

	final Invoice temp;
	try {
	    temp = builder.build(qoDAO::isValidUniqueNumber);
	} catch (NumberOfAttemptsExceedException | NonUniqueNumberException e1) {
	    // it should not happens
	    throw new EJBException(e1.getMessage());
	}

	final Invoice i;
	try {
	    i = invoiceDAO.save(temp);
	} catch (final IllegalArgument e) {
	    // it should not happens
	    throw new EJBException(e.getMessage());
	}

	if (i.optionalConsumerEmail().isPresent()) {
	    i.unlazy();
	    try {
		notifications.send(Notification.builder() //
			.withChannel(NotificationChannel.EMAIL) //
			.withEvent(NotificationEventType.PAYMENT_LINK) //
			.withRecipient(NotificationRecipientType.REQUESTER) //
			.withProperty("paymentUrl", _getDefaultPaymentURI(i).toString()) //
			.forEntity(i) //
			.build());
	    } catch (final IllegalArgument e) {
		// it should not happens
		throw new EJBException(e.getMessage());
	    }
	    logger.FINE.log("Payment accepted notification sent '%1$s'", i);
	}
	return i;
    }

    private void _expireInvoice(final String invoiceNumber)
	    throws IllegalArgumentException, IllegalStateException, InvoiceNotFound {

	MyStrings.requireNonEmpty(invoiceNumber, "invoiceNumber");

	final Invoice i = _invoiceByNumber(invoiceNumber);

	try {
	    i.expire();
	} catch (final IllegalState e) {
	    // payment is inconsistent
	    throw e.getRuntime();
	}
	try {
	    invoiceDAO.save(i);
	} catch (final IllegalArgument e) {
	    // it should not happens
	    throw new EJBException(e.getMessage());
	}
    }

    private void _completeWithUnknownPayment(final String invoiceNumber, final Double paidAmount,
	    final Currency paidCurency, final Instant paidInstant, final String paidReference, final String payerName)
	    throws IllegalArgumentException, IllegalStateException, InvoiceNotFound {

	MyStrings.requireNonEmpty(invoiceNumber, "invoiceNumber");
	MyNumbers.requireNonZero(paidAmount, "paidAmount");
	MyObjects.requireNonNull(paidCurency, "paidCurency");

	final UnknownPayment p1 = UnknownPayment.builder() //
		.withAmount(paidAmount) //
		.withCurrency(paidCurency) //
		.withCreationInstant(MyOptionals.of(paidInstant)) //
		.withReferenceNumber(MyOptionals.of(paidReference)) //
		.withPayerName(MyOptionals.of(payerName)) //
		.build();

	final UnknownPayment p2;
	try {
	    p2 = paymentDAO.save(p1);
	} catch (final IllegalArgument e) {
	    // it should not happens
	    throw new EJBException(e.getMessage());
	}

	final Invoice i1 = _invoiceByNumber(invoiceNumber);
	_invoiceHasPaidBy(i1, p2);
    }

    @EJB
    private BankDAORemote bankDAO;

    private Bank fetchBankWithCardMasked(final String cardMasked) {
	try {
	    final String bin = cardMasked.substring(0, 6);
	    final Bank bank = bankDAO.getByBIN(bin);
	    return bank;
	} catch (Exception e) {
	    return null;
	}
    }

    private void _completeWithQazkomPayment(final String postbackXml)
	    throws IllegalArgumentException, IllegalStateException {

	MyStrings.requireNonEmpty(postbackXml, "postbackXml");

	logger.INFO.log("New postback '%1$s'", postbackXml);

	final QazkomPaymentBuilder builder = QazkomPayment.builder();

	try {
	    builder
		    .fromRawXml(postbackXml)
		    .withBankCertificate(qazkomSettings.QAZKOM_BANK_CERTIFICATE)
		    .withCardIssuingBankFetcher(this::fetchBankWithCardMasked);
	} catch (final IllegalArgumentException e) {
	    // it should not happens
	    throw new EJBException(e.getMessage());
	}

	final QazkomPayment p1 = builder.build();

	final String orderNumber = p1.getOrderNumber();
	MyStrings.requireNonEmpty(orderNumber, "orderNumber");

	try {
	    if (!qpDAO.isUniqueNumber(orderNumber))
		throw MyExceptions.illegalStateFormat("Already processed QazkomPayment with order number %1$s",
			orderNumber);
	} catch (final IllegalArgument e) {
	    // it should not happens
	    throw new EJBException(e.getMessage());
	}

	final QazkomPayment p2;
	try {
	    p2 = qpDAO.save(p1);
	} catch (final IllegalArgument e) {
	    // it should not happens
	    throw new EJBException(e.getMessage());
	}

	logger.INFO.log("QazkomPayment OK - '%1$s'", p2);

	final QazkomOrder o1;
	try {
	    o1 = qoDAO.getByNumber(orderNumber);
	} catch (final IllegalArgument e) {
	    // it should not happens
	    throw new EJBException(e.getMessage());
	} catch (final NotFound e) {
	    throw MyExceptions.illegalArgumentFormat("No QazkomOrder found or reference is invlaid - '%1$s'",
		    orderNumber);
	}
	logger.INFO.log("QazkomOrder OK - '%1$s'", o1);

	try {
	    o1.paidBy(p2);
	} catch (final IllegalArgumentException e) {
	    // it should not happens
	    throw new EJBException(e.getMessage());
	} catch (final IllegalArgument e) {
	    // payment is inconsistent
	    throw e.getRuntime();
	} catch (final IllegalState e) {
	    // order can't be paid
	    throw e.getRuntime();
	}

	final QazkomOrder o2;
	try {
	    o2 = qoDAO.save(o1);
	} catch (final IllegalArgument e) {
	    // it should not happens
	    throw new EJBException(e.getMessage());
	}

	final Invoice i = o2.getForInvoice();
	final Payment p3 = o2.getPayment();
	_invoiceHasPaidBy(i, p3);
    }

    private PaymentMethod _qazkomHttpMethod(final URI postbackURI,
	    final URI failureURI,
	    final URI returnURI,
	    final String invoiceNumber) throws IllegalArgumentException, InvoiceNotFound {

	final Invoice i = _invoiceByNumber(invoiceNumber);
	return _qazkomHttpMethod(postbackURI, failureURI, returnURI, i);
    }

    private PaymentMethod _qazkomHttpMethod(final URI postbackURI,
	    final URI failureURI,
	    final URI returnURI,
	    final Invoice forInvoice) throws IllegalArgumentException {

	MyObjects.requireNonNull(postbackURI, "postbackURI");
	MyObjects.requireNonNull(failureURI, "failureURI");
	MyObjects.requireNonNull(returnURI, "returnURI");
	MyObjects.requireNonNull(forInvoice, "forInvoice");

	final QazkomOrder o;
	{
	    QazkomOrder temp;
	    try {
		temp = qoDAO.getLatestForInvoice(forInvoice);
	    } catch (final IllegalArgument e) {
		// it should not happens
		throw new EJBException(e.getMessage());
	    } catch (final NotFound e) {
		// еще небыло ордеров
		try {
		    temp = QazkomOrder.builder() //
			    .forInvoice(forInvoice) //
			    .withGeneratedNumber() //
			    .withMerchant(qazkomSettings.QAZKOM_MERCHANT_ID, //
				    qazkomSettings.QAZKOM_MERCHANT_NAME, //
				    qazkomSettings.QAZKOM_MERCHANT_CERTIFICATE, //
				    qazkomSettings.QAZKOM_MERCHANT_key) //
			    .build(qoDAO::isValidUniqueNumber);
		} catch (IllegalArgumentException | NumberOfAttemptsExceedException | NonUniqueNumberException e1) {
		    // it should not happens
		    throw new EJBException(e1.getMessage());
		}
		try {
		    temp = qoDAO.save(temp);
		} catch (IllegalArgument e1) {
		    // it should not happens
		    throw new EJBException(e1.getMessage());
		}
	    }
	    o = temp;
	}

	try {
	    final Http http = new Http(qazkomSettings.QAZKOM_EPAY_URI, qazkomSettings.QAZKOM_EPAY_HTTP_METHOD,
		    MyMaps.of(
			    "Signed_Order_B64", o.getOrderDoc().getBase64Xml(), //
			    "template", qazkomSettings.QAZKOM_EPAY_TEMPLATE, //
			    "email", forInvoice.optionalConsumerEmail().orElse(""), //
			    "PostLink", postbackURI.toASCIIString(),
			    "FailurePostLink", failureURI.toASCIIString(),
			    "Language", forInvoice.getConsumerPreferLanguage().getTag(), //
			    "appendix", o.getCartDoc().getBase64Xml(), //
			    "BackLink", returnURI.toString() //
		    ));
	    return new PaymentMethod(http);
	} catch (final IllegalArgumentException e) {
	    // it should not happens
	    throw new EJBException(e.getMessage());
	}

    }

    private String _processQazkomFailure(final String failureXml) throws IllegalArgumentException {

	MyStrings.requireNonEmpty(failureXml, "failureXml");

	logger.INFO.log("New failure '%1$s'", failureXml);

	final QazkomError qeNew = QazkomError.builder() //
		.fromRawXml(failureXml) //
		.build();

	final QazkomError qe;
	try {
	    qe = qeDAO.save(qeNew);
	} catch (final IllegalArgument e) {
	    // it should not happens
	    throw e.getRuntime();
	}

	final String orderNumber = qe.getOrderNumber();

	final QazkomOrder qo;
	try {
	    qo = qoDAO.getByNumber(orderNumber);
	} catch (NotFound | IllegalArgument e) {
	    throw MyExceptions.illegalArgumentFormat("No QazkomOrder found or order number is invlaid - '%1$s'",
		    orderNumber);
	}
	logger.INFO.log("QazkomOrder OK - '%1$s'", qo);

	try {
	    qo.attachError(qe);
	} catch (final IllegalArgumentException e) {
	    // it should not happens
	    throw new EJBException(e.getMessage());
	} catch (final IllegalArgument e1) {
	    // error is inconsistent
	    throw e1.getRuntime();
	}

	try {
	    qoDAO.save(qo);
	} catch (final IllegalArgument e) {
	    // it should not happens
	    throw new EJBException(e.getMessage());
	}
	try {
	    qeDAO.save(qe);
	} catch (final IllegalArgument e) {
	    // it should not happens
	    throw new EJBException(e.getMessage());
	}

	return qe.getMessage();
    }

    @Inject
    @JmsDestination(EpaymentDestinations.INVOICE_HAS_PAID)
    private JmsEventNotificatorClient<InvoiceHasPaidJmsEvent> invoiceHasPaidEventNotificatorClient;

    private void _invoiceHasPaidBy(final Invoice i1, final Payment p1)
	    throws IllegalArgumentException, IllegalStateException {

	// it should not happens
	MyObjects.requireNonNull(EJBException::new, i1, "invoice");
	// it should not happens
	MyObjects.requireNonNull(EJBException::new, p1, "payment");

	try {
	    if (i1.isExpired())
		i1.pending();
	    i1.paidBy(p1);
	} catch (final IllegalArgumentException e) {
	    // it should not happens
	    throw new EJBException(e.getMessage());
	} catch (final IllegalArgument e) {
	    // payment is inconsistent
	    throw e.getRuntime();
	} catch (final IllegalState e) {
	    // invoice can't be paid
	    throw e.getRuntime();
	}

	final Invoice i2;
	try {
	    i2 = invoiceDAO.save(i1);
	} catch (final IllegalArgument e) {
	    // it should not happens
	    throw new EJBException(e.getMessage());
	}

	logger.INFO.log("Ivoice has paid successfuly '%1$s'", i2);

	if (i2.optionalConsumerEmail().isPresent()) {
	    i2.unlazy();
	    try {
		notifications.send(Notification.builder() //
			.withChannel(NotificationChannel.EMAIL) //
			.withEvent(NotificationEventType.PAYMENT_SUCCESS) //
			.withRecipient(NotificationRecipientType.REQUESTER) //
			.forEntity(i2) //
			.build());
	    } catch (final IllegalArgument e) {
		// it should not happens
		throw new EJBException(e.getMessage());
	    }
	}

	{
	    final String methodName = p1.getMethod().name();
	    final Instant paid = p1.getCreated();
	    final Double amount = p1.getAmount();
	    final Currency currency = p1.getCurrency();
	    final String invoiceNumber = i1.getNumber();
	    final String externalId = i1.getExternalId();

	    final String card = MyOptionals.of(p1) //
		    .map(MyObjects.castOrNull(QazkomPayment.class)) //
		    .map(QazkomPayment::getCardNumber) //
		    .orElse(null);

	    final String cardBank = MyOptionals.of(p1) //
		    .map(MyObjects.castOrNull(QazkomPayment.class)) //
		    .map(QazkomPayment::getCardIssuingBank) //
		    .map(Bank::getCode) //
		    .orElse(null);

	    final String payerName = p1.getPayerName();
	    final String ref = p1.getReference();

	    final InvoiceHasPaidJmsEvent ev = new InvoiceHasPaidJmsEvent();
	    ev.setAmount(amount);
	    ev.setCurrency(currency);
	    ev.setInstant(paid);
	    ev.setInvoiceNumber(invoiceNumber);
	    ev.setMethod(methodName);
	    ev.setReferenceNumber(ref);
	    ev.setPaymentCard(card);
	    ev.setPaymentCardBank(cardBank);
	    ev.setExternalId(externalId);
	    ev.setPayerName(payerName);

	    invoiceHasPaidEventNotificatorClient.eventNotify(ev);
	}
    }
}
