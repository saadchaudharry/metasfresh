/*
 * #%L
 * de.metas.cucumber
 * %%
 * Copyright (C) 2021 metas GmbH
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 2 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public
 * License along with this program. If not, see
 * <http://www.gnu.org/licenses/gpl-2.0.html>.
 * #L%
 */

package de.metas.cucumber.stepdefs;

import de.metas.common.util.Check;
import de.metas.common.util.EmptyUtil;
import de.metas.currency.Currency;
import de.metas.currency.CurrencyCode;
import de.metas.currency.ICurrencyDAO;
import de.metas.document.DocTypeId;
import de.metas.document.DocTypeQuery;
import de.metas.document.IDocTypeDAO;
import de.metas.document.engine.IDocument;
import de.metas.document.engine.IDocumentBL;
import de.metas.order.IOrderBL;
import de.metas.order.OrderId;
import de.metas.order.process.C_Order_CreatePOFromSOs;
import de.metas.process.AdProcessId;
import de.metas.process.IADProcessDAO;
import de.metas.process.ProcessInfo;
import de.metas.util.Services;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import lombok.NonNull;
import org.adempiere.ad.dao.IQueryBL;
import org.adempiere.model.InterfaceWrapperHelper;
import org.compiere.model.I_C_BPartner;
import org.compiere.model.I_C_BPartner_Location;
import org.compiere.model.I_C_DocType;
import org.compiere.model.I_C_Order;
import org.compiere.model.I_C_OrderLine;
import org.compiere.util.TimeUtil;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import static de.metas.cucumber.stepdefs.StepDefConstants.TABLECOLUMN_IDENTIFIER;
import static org.adempiere.model.InterfaceWrapperHelper.load;
import static org.adempiere.model.InterfaceWrapperHelper.newInstance;
import static org.adempiere.model.InterfaceWrapperHelper.saveRecord;
import static org.assertj.core.api.Assertions.*;
import static org.compiere.model.I_C_BPartner_Location.COLUMNNAME_C_BPartner_Location_ID;
import static org.compiere.model.I_C_DocType.COLUMNNAME_DocBaseType;
import static org.compiere.model.I_C_DocType.COLUMNNAME_DocSubType;
import static org.compiere.model.I_C_Order.COLUMNNAME_C_BPartner_ID;
import static org.compiere.model.I_C_Order.COLUMNNAME_C_Order_ID;
import static org.compiere.model.I_C_Order.COLUMNNAME_DocStatus;
import static org.compiere.model.I_C_Order.COLUMNNAME_Link_Order_ID;
import static org.compiere.model.I_C_Order.COLUMNNAME_Processing;

public class C_Order_StepDef
{
	private final IDocumentBL documentBL = Services.get(IDocumentBL.class);
	private final IQueryBL queryBL = Services.get(IQueryBL.class);
	private final IADProcessDAO adProcessDAO = Services.get(IADProcessDAO.class);
	private final IOrderBL orderBL = Services.get(IOrderBL.class);
	private final ICurrencyDAO currencyDAO = Services.get(ICurrencyDAO.class);
	private final IDocTypeDAO docTypeDAO = Services.get(IDocTypeDAO.class);

	private final C_BPartner_StepDefData bpartnerTable;
	private final C_Order_StepDefData orderTable;
	private final StepDefData<I_C_BPartner_Location> bpartnerLocationTable;

	public C_Order_StepDef(
			@NonNull final C_BPartner_StepDefData bpartnerTable,
			@NonNull final C_Order_StepDefData orderTable,
			@NonNull final StepDefData<I_C_BPartner_Location> bpartnerLocationTable)
	{
		this.bpartnerTable = bpartnerTable;
		this.orderTable = orderTable;
		this.bpartnerLocationTable = bpartnerLocationTable;
	}

	@Given("metasfresh contains C_Orders:")
	public void metasfresh_contains_c_invoice_candidates(@NonNull final DataTable dataTable)
	{
		final List<Map<String, String>> tableRows = dataTable.asMaps(String.class, String.class);
		for (final Map<String, String> tableRow : tableRows)
		{
			final String bpartnerIdentifier = DataTableUtil.extractStringForColumnName(tableRow, COLUMNNAME_C_BPartner_ID + "." + StepDefConstants.TABLECOLUMN_IDENTIFIER);
			final I_C_BPartner bpartner = bpartnerTable.get(bpartnerIdentifier);
			final int warehouseId = DataTableUtil.extractIntOrMinusOneForColumnName(tableRow, "OPT.Warehouse_ID");
			final String poReference = DataTableUtil.extractStringOrNullForColumnName(tableRow, "OPT." + I_C_Order.COLUMNNAME_POReference);
			final int paymentTermId = DataTableUtil.extractIntOrMinusOneForColumnName(tableRow, "OPT." + I_C_Order.COLUMNNAME_C_PaymentTerm_ID);

			final I_C_Order order = newInstance(I_C_Order.class);
			order.setAD_Org_ID(StepDefConstants.ORG_ID.getRepoId());
			order.setC_BPartner_ID(bpartner.getC_BPartner_ID());
			order.setM_Warehouse_ID(warehouseId);
			order.setIsSOTrx(DataTableUtil.extractBooleanForColumnName(tableRow, I_C_Order.COLUMNNAME_IsSOTrx));
			order.setDateOrdered(DataTableUtil.extractDateTimestampForColumnName(tableRow, I_C_Order.COLUMNNAME_DateOrdered));

			final ZonedDateTime prepartionDate = DataTableUtil.extractZonedDateTimeOrNullForColumnName(tableRow, "OPT." + I_C_Order.COLUMNNAME_PreparationDate);
			if (prepartionDate != null)
			{
				order.setPreparationDate(TimeUtil.asTimestamp(prepartionDate));
				order.setDatePromised(TimeUtil.asTimestamp(prepartionDate));
			}

			if (EmptyUtil.isNotBlank(poReference))
			{
				order.setPOReference(poReference);
			}

			if (paymentTermId > 0)
			{
				order.setC_PaymentTerm_ID(paymentTermId);
			}

			saveRecord(order);

			orderTable.putOrReplace(DataTableUtil.extractRecordIdentifier(tableRow, I_C_Order.COLUMNNAME_C_Order_ID), order);
		}
	}

	@Given("^the order identified by (.*) is completed$")
	public void order_is_completed(@NonNull final String orderIdentifier)
	{
		final I_C_Order order = orderTable.get(orderIdentifier);
		order.setDocAction(IDocument.ACTION_Complete); // we need this because otherwise MOrder.completeIt() won't complete it
		documentBL.processEx(order, IDocument.ACTION_Complete, IDocument.STATUS_Completed);
	}

	@Given("generate PO from SO is invoked with parameters:")
	public void generate_PO_from_SO_invoked(@NonNull final DataTable dataTable)
	{
		final List<Map<String, String>> tableRows = dataTable.asMaps(String.class, String.class);
		for (final Map<String, String> tableRow : tableRows)
		{
			final String bpartnerIdentifier = DataTableUtil.extractStringForColumnName(tableRow, COLUMNNAME_C_BPartner_ID + "." + StepDefConstants.TABLECOLUMN_IDENTIFIER);
			final String orderIdentifier = DataTableUtil.extractStringForColumnName(tableRow, COLUMNNAME_C_Order_ID + "." + StepDefConstants.TABLECOLUMN_IDENTIFIER);
			final String purchaseType = DataTableUtil.extractStringForColumnName(tableRow, "PurchaseType");

			final I_C_Order order = orderTable.get(orderIdentifier);
			final I_C_BPartner bpartner = bpartnerTable.get(bpartnerIdentifier);

			final AdProcessId processId = adProcessDAO.retrieveProcessIdByClass(C_Order_CreatePOFromSOs.class);

			final ProcessInfo.ProcessInfoBuilder processInfoBuilder = ProcessInfo.builder();
			processInfoBuilder.setAD_Process_ID(processId.getRepoId());
			processInfoBuilder.addParameter("DatePromised_From", Timestamp.from(Instant.now()));
			processInfoBuilder.addParameter("DatePromised_To", Timestamp.from(Instant.now()));
			processInfoBuilder.addParameter("C_BPartner_ID", bpartner.getC_BPartner_ID());
			processInfoBuilder.addParameter("C_Order_ID", order.getC_Order_ID());
			processInfoBuilder.addParameter("TypeOfPurchase", purchaseType);

			processInfoBuilder
					.buildAndPrepareExecution()
					.executeSync()
					.getResult();
		}

	}

	@Then("the order is created:")
	public void thePurchaseOrderIsCreated(@NonNull final DataTable dataTable)
	{
		final List<Map<String, String>> tableRows = dataTable.asMaps(String.class, String.class);
		for (final Map<String, String> tableRow : tableRows)
		{
			final String linkedOrderIdentifier = DataTableUtil.extractStringForColumnName(tableRow, COLUMNNAME_Link_Order_ID + ".Identifier");

			final I_C_Order purchaseOrder = Services.get(IQueryBL.class)
					.createQueryBuilder(I_C_Order.class)
					.addOnlyActiveRecordsFilter()
					.addEqualsFilter(I_C_Order.COLUMNNAME_Link_Order_ID, orderTable.get(linkedOrderIdentifier).getC_Order_ID())
					.create()
					.firstOnly(I_C_Order.class);

			final boolean isSOTrx = DataTableUtil.extractBooleanForColumnName(tableRow, I_C_Order.COLUMNNAME_IsSOTrx);
			assertThat(purchaseOrder.isSOTrx()).isEqualTo(isSOTrx);

			final I_C_DocType docType = load(purchaseOrder.getC_DocTypeTarget_ID(), I_C_DocType.class);

			final String docBaseType = DataTableUtil.extractStringForColumnName(tableRow, COLUMNNAME_DocBaseType);
			assertThat(docType.getDocBaseType()).isEqualTo(docBaseType);

			final String docSubType = DataTableUtil.extractStringForColumnName(tableRow, COLUMNNAME_DocSubType);
			assertThat(docType.getDocSubType()).isEqualTo(docSubType);
		}
	}

	@Then("the sales order identified by {string} is closed")
	public void salesOrderIsClosed(@NonNull final String orderIdentifier)
	{
		final I_C_Order order = orderTable.get(orderIdentifier);
		final I_C_Order salesOrder = orderBL.getById(OrderId.ofRepoId(order.getC_Order_ID()));

		assertThat(salesOrder.getDocStatus()).isEqualTo(IDocument.STATUS_Closed);
	}

	@Then("a PurchaseOrder with externalId {string} is created after not more than {int} seconds and has values")
	public void verifyOrder(final String externalId, final int timeoutSec, @NonNull final DataTable dataTable) throws InterruptedException
	{
		final Map<String, String> dataTableRow = dataTable.asMaps().get(0);

		final Supplier<Boolean> purchaseOrderQueryExecutor = () -> {

			final I_C_Order purchaseOrderRecord = queryBL.createQueryBuilder(I_C_Order.class)
					.addEqualsFilter(I_C_Order.COLUMNNAME_ExternalId, externalId)
					.create()
					.firstOnly(I_C_Order.class);

			return purchaseOrderRecord != null;
		};

		StepDefUtil.tryAndWait(timeoutSec, 500, purchaseOrderQueryExecutor);

		final I_C_Order purchaseOrderRecord = queryBL.createQueryBuilder(I_C_Order.class)
				.addEqualsFilter(I_C_Order.COLUMNNAME_ExternalId, externalId)
				.create()
				.firstOnlyNotNull(I_C_Order.class);

		final String externalPurchaseOrderUrl = DataTableUtil.extractStringForColumnName(dataTableRow, I_C_Order.COLUMNNAME_ExternalPurchaseOrderURL);
		assertThat(purchaseOrderRecord.getExternalPurchaseOrderURL()).isEqualTo(externalPurchaseOrderUrl);
		
		final String poReference = DataTableUtil.extractStringForColumnName(dataTableRow, I_C_Order.COLUMNNAME_POReference);
		assertThat(purchaseOrderRecord.getPOReference()).isEqualTo(poReference);

		final String orderIdentifier = DataTableUtil.extractStringOrNullForColumnName(dataTableRow, "OPT." + COLUMNNAME_C_Order_ID + "." + TABLECOLUMN_IDENTIFIER);
		if (EmptyUtil.isNotBlank(orderIdentifier))
		{
			orderTable.putOrReplace(orderIdentifier, purchaseOrderRecord);
		}
	}

	@And("validate the created orders")
	public void validate_created_order(@NonNull final DataTable table)
	{
		final Map<String, String> row = table.asMaps().get(0);
		validateOrder(row);
	}

	@And("update order")
	public void update_order(@NonNull final DataTable dataTable) throws InterruptedException
	{
		final List<Map<String, String>> tableRows = dataTable.asMaps(String.class, String.class);
		for (final Map<String, String> tableRow : tableRows)
		{
			final String orderIdentifier = DataTableUtil.extractStringForColumnName(tableRow, COLUMNNAME_C_Order_ID + "." + TABLECOLUMN_IDENTIFIER);
			final I_C_Order order = orderTable.get(orderIdentifier);

			final String docBaseType = DataTableUtil.extractStringForColumnName(tableRow, COLUMNNAME_DocBaseType);
			final String docSubType = DataTableUtil.extractStringForColumnName(tableRow, COLUMNNAME_DocSubType);

			final DocTypeQuery docTypeQuery = DocTypeQuery.builder()
					.docBaseType(docBaseType)
					.docSubType(docSubType)
					.adClientId(order.getAD_Client_ID())
					.adOrgId(order.getAD_Org_ID())
					.build();

			final DocTypeId docTypeId = docTypeDAO.getDocTypeId(docTypeQuery);

			order.setC_DocType_ID(docTypeId.getRepoId());
			order.setC_DocTypeTarget_ID(docTypeId.getRepoId());

			InterfaceWrapperHelper.saveRecord(order);

			orderTable.putOrReplace(orderIdentifier, order);
		}
	}

	@And("^after not more than (.*)s the order is found$")
	public void lookup_order(final int timeoutSec, @NonNull final DataTable dataTable) throws InterruptedException
	{
		final List<Map<String, String>> tableRows = dataTable.asMaps(String.class, String.class);
		for (final Map<String, String> tableRow : tableRows)
		{
			StepDefUtil.tryAndWait(timeoutSec, 500, () -> retrieveOrder(tableRow));
		}
	}

	private void validateOrder(@NonNull final Map<String, String> row)
	{
		final String identifier = DataTableUtil.extractStringForColumnName(row, "C_Order_ID.Identifier");

		final String bpartnerIdentifier = DataTableUtil.extractStringForColumnName(row, I_C_BPartner.COLUMNNAME_C_BPartner_ID + "." + TABLECOLUMN_IDENTIFIER);
		final I_C_BPartner bPartner = bpartnerTable.get(bpartnerIdentifier);
		assertThat(bPartner).isNotNull();

		final String bpartnerLocationIdentifier = DataTableUtil.extractStringForColumnName(row, COLUMNNAME_C_BPartner_Location_ID + "." + TABLECOLUMN_IDENTIFIER);
		final I_C_BPartner_Location bPartnerLocation = bpartnerLocationTable.get(bpartnerLocationIdentifier);
		final Timestamp dateOrdered = DataTableUtil.extractDateTimestampForColumnName(row, "dateordered");
		final String docbasetype = DataTableUtil.extractStringForColumnName(row, "docbasetype");
		final String currencyCode = DataTableUtil.extractStringForColumnName(row, "currencyCode");
		final String deliveryRule = DataTableUtil.extractStringForColumnName(row, "deliveryRule");
		final String deliveryViaRule = DataTableUtil.extractStringForColumnName(row, "deliveryViaRule");
		final boolean processed = DataTableUtil.extractBooleanForColumnNameOr(row, "processed", false);
		final String externalId = DataTableUtil.extractStringForColumnName(row, "externalId");
		final String docStatus = DataTableUtil.extractStringForColumnName(row, "docStatus");
		final String bpartnerName = DataTableUtil.extractStringOrNullForColumnName(row, "OPT." + I_C_Order.COLUMNNAME_BPartnerName);

		final I_C_Order order = orderTable.get(identifier);

		assertThat(order.getExternalId()).isEqualTo(externalId);
		assertThat(order.getC_BPartner_ID()).isEqualTo(bPartner.getC_BPartner_ID());
		assertThat(order.getC_BPartner_Location_ID()).isEqualTo(bPartnerLocation.getC_BPartner_Location_ID());
		assertThat(order.getDateOrdered()).isEqualTo(dateOrdered);
		assertThat(order.getDeliveryRule()).isEqualTo(deliveryRule);
		assertThat(order.getDeliveryViaRule()).isEqualTo(deliveryViaRule);
		assertThat(order.isProcessed()).isEqualTo(processed);
		assertThat(order.getDocStatus()).isEqualTo(docStatus);

		if(Check.isNotBlank(bpartnerName))
		{
			assertThat(order.getBPartnerName()).isEqualTo(bpartnerName);
		}

		final Currency currency = currencyDAO.getByCurrencyCode(CurrencyCode.ofThreeLetterCode(currencyCode));
		assertThat(order.getC_Currency_ID()).isEqualTo(currency.getId().getRepoId());

		final I_C_DocType docType = docTypeDAO.getById(order.getC_DocType_ID());
		assertThat(docType).isNotNull();
		assertThat(docType.getDocBaseType()).isEqualTo(docbasetype);
	}

	@Then("the following group compensation order lines were created for externalHeaderId: {string}")
	public void verifyOrderLines(final String externalHeaderId, @NonNull final DataTable dataTable)
	{
		final List<Map<String, String>> tableRows = dataTable.asMaps(String.class, String.class);
		for (final Map<String, String> tableRow : tableRows)
		{
			final int line = DataTableUtil.extractIntForColumnName(tableRow, I_C_OrderLine.COLUMNNAME_Line);
			final Boolean isGroupCompensationLine = DataTableUtil.extractBooleanForColumnName(tableRow, I_C_OrderLine.COLUMNNAME_IsGroupCompensationLine);
			final BigDecimal groupCompensationPercentage = DataTableUtil.extractBigDecimalForColumnName(tableRow, I_C_OrderLine.COLUMNNAME_GroupCompensationPercentage);
			final String groupCompensationType = DataTableUtil.extractStringForColumnName(tableRow, I_C_OrderLine.COLUMNNAME_GroupCompensationType);
			final String groupCompensationAmtType = DataTableUtil.extractStringForColumnName(tableRow, I_C_OrderLine.COLUMNNAME_GroupCompensationAmtType);

			final I_C_Order orderRecord = queryBL.createQueryBuilder(I_C_Order.class)
					.addEqualsFilter(I_C_Order.COLUMNNAME_ExternalId, externalHeaderId)
					.create()
					.firstOnlyNotNull(I_C_Order.class);

			final Optional<I_C_OrderLine> orderLine = queryBL.createQueryBuilder(I_C_OrderLine.class)
					.addEqualsFilter(I_C_OrderLine.COLUMNNAME_C_Order_ID, orderRecord.getC_Order_ID())
					.addEqualsFilter(I_C_OrderLine.COLUMNNAME_Line, line)
					.addEqualsFilter(I_C_OrderLine.COLUMNNAME_IsGroupCompensationLine, isGroupCompensationLine)
					.addEqualsFilter(I_C_OrderLine.COLUMNNAME_GroupCompensationPercentage, groupCompensationPercentage)
					.addEqualsFilter(I_C_OrderLine.COLUMNNAME_GroupCompensationType, groupCompensationType)
					.addEqualsFilter(I_C_OrderLine.COLUMNNAME_GroupCompensationAmtType, groupCompensationAmtType)
					.create()
					.firstOnlyOptional(I_C_OrderLine.class);

			assertThat(orderLine).isPresent();
		}
	}

	@NonNull
	private Boolean retrieveOrder(@NonNull final Map<String, String> row)
	{
		final String docStatus = DataTableUtil.extractStringForColumnName(row, COLUMNNAME_DocStatus);

		final String orderIdentifier = DataTableUtil.extractStringForColumnName(row, COLUMNNAME_C_Order_ID + "." + TABLECOLUMN_IDENTIFIER);
		final I_C_Order order = orderTable.get(orderIdentifier);

		final I_C_Order orderRecord = queryBL.createQueryBuilder(I_C_Order.class)
				.addEqualsFilter(COLUMNNAME_C_Order_ID, order.getC_Order_ID())
				.addEqualsFilter(COLUMNNAME_DocStatus, docStatus)
				.addEqualsFilter(COLUMNNAME_Processing, false)
				.create()
				.firstOnlyOrNull(I_C_Order.class);

		if (orderRecord == null)
		{
			return false;
		}

		orderTable.putOrReplace(orderIdentifier, orderRecord);
		return true;
	}
}