@from:cucumber
Feature: sales order

  Background:
    Given the existing user with login 'metasfresh' receives a random a API token for the existing role with name 'WebUI'
    And metasfresh has date and time 2021-04-16T13:30:13+01:00[Europe/Berlin]

  @from:cucumber
  Scenario: we can create and complete a sales order
    Given metasfresh has date and time 2021-04-16T13:30:13+01:00[Europe/Berlin]
    And metasfresh contains M_Products:
      | Identifier | Name            |
      | p_1        | salesProduct_12 |
    And metasfresh contains M_PricingSystems
      | Identifier | Name                | Value                | OPT.Description            | OPT.IsActive |
      | ps_1       | pricing_system_name | pricing_system_value | pricing_system_description | true         |
    And metasfresh contains M_PriceLists
      | Identifier | M_PricingSystem_ID.Identifier | OPT.C_Country.CountryCode | C_Currency.ISO_Code | Name            | OPT.Description | SOTrx | IsTaxIncluded | PricePrecision | OPT.IsActive |
      | pl_1       | ps_1                          | DE                        | EUR                 | price_list_name | null            | true  | false         | 2              | true         |
    And metasfresh contains M_PriceList_Versions
      | Identifier | M_PriceList_ID.Identifier | Name           | ValidFrom  |
      | plv_1      | pl_1                      | salesOrder-PLV | 2021-04-01 |
    And metasfresh contains M_ProductPrices
      | Identifier | M_PriceList_Version_ID.Identifier | M_Product_ID.Identifier | PriceStd | C_UOM_ID.X12DE355 | C_TaxCategory_ID.InternalName |
      | pp_1       | plv_1                             | p_1                     | 10.0     | PCE               | Normal                        |
    And metasfresh contains C_BPartners:
      | Identifier    | Name        | OPT.IsVendor | OPT.IsCustomer | M_PricingSystem_ID.Identifier |
      | endcustomer_1 | Endcustomer | N            | Y              | ps_1                          |
    And metasfresh contains C_Orders:
      | Identifier | IsSOTrx | C_BPartner_ID.Identifier | DateOrdered |
      | o_1        | true    | endcustomer_1            | 2021-04-17  |
    And metasfresh contains C_OrderLines:
      | Identifier | C_Order_ID.Identifier | M_Product_ID.Identifier | QtyEntered |
      | ol_1       | o_1                   | p_1                     | 10         |
    When the order identified by o_1 is completed
    Then after not more than 30s, M_ShipmentSchedules are found:
      | Identifier | C_OrderLine_ID.Identifier | IsToRecompute |
      | s_ol_1     | ol_1                      | N             |

  @from:cucumber
  Scenario: we can generate a purchase order from a sales order
    And metasfresh contains M_Products:
      | Identifier | Name            |
      | p_2        | salesProduct_72 |
    And metasfresh contains M_PricingSystems
      | Identifier | Name                   | Value                   | OPT.Description            | OPT.IsActive |
      | ps_2       | pricing_system_name_72 | pricing_system_value_72 | pricing_system_description | true         |
    And metasfresh contains M_PriceLists
      | Identifier | M_PricingSystem_ID.Identifier | OPT.C_Country.CountryCode | C_Currency.ISO_Code | Name               | OPT.Description | SOTrx | IsTaxIncluded | PricePrecision | OPT.IsActive |
      | pl_2       | ps_2                          | DE                        | EUR                 | price_list_name_72 | null            | true  | false         | 2              | true         |
      | pl_3       | ps_2                          | DE                        | EUR                 | price_list_name_73 | null            | false | false         | 2              | true         |
    And metasfresh contains M_PriceList_Versions
      | Identifier | M_PriceList_ID.Identifier | Name                 | ValidFrom  |
      | plv_2      | pl_2                      | salesOrder-PLV_72    | 2021-04-01 |
      | plv_3      | pl_3                      | purchaseOrder-PLV_72 | 2021-04-01 |
    And metasfresh contains M_ProductPrices
      | Identifier | M_PriceList_Version_ID.Identifier | M_Product_ID.Identifier | PriceStd | C_UOM_ID.X12DE355 | C_TaxCategory_ID.InternalName |
      | pp_2       | plv_2                             | p_2                     | 10.0     | PCE               | Normal                        |
      | pp_3       | plv_3                             | p_2                     | 10.0     | PCE               | Normal                        |
    And metasfresh contains C_BPartners:
      | Identifier    | Name           | OPT.IsVendor | OPT.IsCustomer | M_PricingSystem_ID.Identifier |
      | endcustomer_2 | Endcustomer_72 | N            | Y              | ps_2                          |
      | vendor_2      | vendor_72      | Y            | Y              | ps_2                          |
    And metasfresh contains C_BPartner_Products:
      | C_BPartner_ID.Identifier | M_Product_ID.Identifier |
      | vendor_2                 | p_2                     |
    And metasfresh contains C_Orders:
      | Identifier | IsSOTrx | C_BPartner_ID.Identifier | DateOrdered | POReference | C_Payment_ID |
      | o_2        | true    | endcustomer_2            | 2021-04-17  | po_ref_mock | 1000002      |
    And metasfresh contains C_OrderLines:
      | Identifier | C_Order_ID.Identifier | M_Product_ID.Identifier | QtyEntered |
      | ol_2       | o_2                   | p_2                     | 10         |
    And the order identified by o_2 is completed
    And after not more than 10s, M_ShipmentSchedules are found:
      | Identifier | C_OrderLine_ID.Identifier | IsToRecompute |
      | s_ol_2     | ol_2                      | N             |
    When generate PO from SO is invoked with parameters:
      | C_BPartner_ID.Identifier | C_Order_ID.Identifier | PurchaseType |
      | vendor_2                 | o_2                   | Mediated     |
    Then the order is created:
      | Link_Order_ID.Identifier | IsSOTrx | DocBaseType | DocSubType |
      | o_2                      | false   | POO         | MED        |
    And the mediated purchase order linked to order 'o_2' has lines:
      | QtyOrdered | LineNetAmt | M_Product_ID.Identifier |
      | 10         | 100        | p_2                     |
    And the sales order identified by 'o_2' is closed
    And the shipment schedule identified by s_ol_2 is processed after not more than 10 seconds


  @from:cucumber
  Scenario: we can generate a purchase order from a sales order, exploding BOM components
  AND metasfresh contains organizations
  | Identifier | Name |
  | org_1      | org_1 |
    And metasfresh contains M_Products:
      | Identifier | Name               |
      | p_3        | salesProduct_67    |
      | p_31       | salesProduct_67_1  |
      | p_32       | salesProduct_67_2  |
      | p_33       | sales_Service_67_3 |
    And metasfresh contains M_PricingSystems
      | Identifier | Name                   | Value                   | OPT.Description            | OPT.IsActive |
      | ps_3       | pricing_system_name_67 | pricing_system_value_67 | pricing_system_description | true         |
    And metasfresh contains M_PriceLists
      | Identifier | M_PricingSystem_ID.Identifier | OPT.C_Country.CountryCode | C_Currency.ISO_Code | Name                    | OPT.Description | SOTrx | IsTaxIncluded | PricePrecision | OPT.IsActive |
      | pl_67_1    | ps_3                          | DE                        | EUR                 | price_list_name_67_main | null            | true  | false         | 2              | true         |
      | pl_67_2    | ps_3                          | DE                        | EUR                 | price_list_name_67_1    | null            | false | false         | 2              | true         |
    And metasfresh contains M_PriceList_Versions
      | Identifier | M_PriceList_ID.Identifier | Name                 | ValidFrom  |
      | plv_67_1   | pl_67_1                   | salesOrder-PLV_67    | 2021-04-01 |
      | plv_67_2   | pl_67_2                   | purchaseOrder-PLV_67 | 2021-04-01 |
    And metasfresh contains M_ProductPrices
      | Identifier | M_PriceList_Version_ID.Identifier | M_Product_ID.Identifier | PriceStd | C_UOM_ID.X12DE355 | C_TaxCategory_ID.InternalName |
      | pp_67_1    | plv_67_1                          | p_3                     | 0.0      | PCE               | Normal                        |
      | pp_67_2    | plv_67_1                          | p_33                    | 10.0     | PCE               | Normal                        |
      | pp_67_4    | plv_67_2                          | p_31                    | 0.0      | PCE               | Normal                        |
      | pp_67_5    | plv_67_2                          | p_32                    | 0.0      | PCE               | Normal                        |
    And metasfresh contains PP_Product_BOMVersions:
      | Identifier | M_Product_ID.Identifier | Name                |
      | ppbv_67    | p_3                     | p_3_bomversion_name |
    And metasfresh contains PP_Product_BOM:
      | Identifier | M_Product_ID.Identifier | Name         | BOMType       | BOMUse        | C_UOM_ID.X12DE355 | PP_Product_BOMVersions_ID.Identifier | ValidFrom  |
      | ppb_67     | p_3                     | p_3_bom_name | CurrentActive | Manufacturing | PCE               | ppbv_67                              | 2021-04-01 |
    And metasfresh contains PP_Product_BOMLines:
      | Identifier | PP_Product_BOM_ID.Identifier | M_Product_ID.Identifier | QtyBOM | C_UOM_ID.X12DE355 | ComponentType | ValidFrom  | Line |
      | ppbl_67_1  | ppb_67                       | p_31                    | 3      | PCE               | CO            | 2021-04-01 | 100  |
      | ppbl_67_2  | ppb_67                       | p_32                    | 4      | PCE               | CO            | 2021-04-01 | 200  |
    And metasfresh contains C_BPartners:
      | Identifier     | Name           | OPT.IsVendor | OPT.IsCustomer | M_PricingSystem_ID.Identifier |
      | endcustomer_67 | Endcustomer_67 | N            | Y              | ps_3                          |
      | vendor_67      | vendor_67      | Y            | Y              | ps_3                          |
    And metasfresh contains C_BPartner_Products:
      | C_BPartner_ID.Identifier | M_Product_ID.Identifier |
      | vendor_67                | p_31                    |
      | vendor_67                | p_32                    |
    And metasfresh contains C_Orders:
      | Identifier | IsSOTrx | C_BPartner_ID.Identifier | DateOrdered | POReference     | C_Payment_ID |
      | o_3        | true    | endcustomer_67           | 2021-04-17  | po_ref_BOM_mock | 1000002      |
    And metasfresh contains C_OrderLines:
      | Identifier | C_Order_ID.Identifier | M_Product_ID.Identifier | QtyEntered |
      | ol_3_1     | o_3                   | p_3                     | 10         |
      | ol_3_2     | o_3                   | p_33                    | 10         |
    And the order identified by o_3 is completed
    And after not more than 10s, M_ShipmentSchedules are found:
      | Identifier | C_OrderLine_ID.Identifier | IsToRecompute |
      | s_ol_3     | ol_3_1                    | N             |
    When generate PO from SO is invoked with parameters:
      | C_BPartner_ID.Identifier | C_Order_ID.Identifier | PurchaseType | IsPurchaseBOMComponents |
      | vendor_67                | o_3                   | Mediated     | true                    |
    Then the order is created:
      | Link_Order_ID.Identifier | IsSOTrx | DocBaseType | DocSubType |
      | o_3                      | false   | POO         | MED        |
    And the mediated purchase order linked to order 'o_3' has lines:
      | QtyOrdered | LineNetAmt | M_Product_ID.Identifier |
      | 30         | 0          | p_31                    |
      | 40         | 0          | p_32                    |
    And the sales order identified by 'o_3' is closed
    And the shipment schedule identified by s_ol_3 is processed after not more than 10 seconds