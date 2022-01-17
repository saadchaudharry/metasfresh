/*
 * #%L
 * de.metas.swat.base
 * %%
 * Copyright (C) 2022 metas GmbH
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

package de.metas.ui.web.order;

import com.google.common.collect.ImmutableList;
import de.metas.order.compensationGroup.GroupCreateRequest;
import de.metas.order.compensationGroup.GroupId;
import de.metas.order.compensationGroup.OrderGroupRepository;
import de.metas.product.IProductBL;
import de.metas.product.ProductId;
import de.metas.uom.UomId;
import de.metas.util.Services;
import lombok.Builder;
import lombok.NonNull;
import lombok.Singular;
import org.adempiere.mm.attributes.AttributeSetInstanceId;
import org.adempiere.mm.attributes.api.IAttributeSetInstanceBL;
import org.adempiere.mm.attributes.api.ImmutableAttributeSet;
import org.compiere.SpringContextHolder;
import org.eevolution.api.BOMComponentType;
import org.eevolution.api.BOMUse;
import org.eevolution.api.IProductBOMBL;
import org.eevolution.api.IProductBOMDAO;
import org.eevolution.api.ProductBOMLineId;
import org.eevolution.model.I_PP_Product_BOM;
import org.eevolution.model.I_PP_Product_BOMLine;

import javax.annotation.Nullable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

@Builder(toBuilder = true)
public class BOMExploderCommand
{
	private final OrderGroupRepository orderGroupsRepo = SpringContextHolder.instance.getBean(OrderGroupRepository.class);
	private final IProductBL productBL = Services.get(IProductBL.class);
	private final IAttributeSetInstanceBL asiBL = Services.get(IAttributeSetInstanceBL.class);
	private final IProductBOMDAO bomsRepo = Services.get(IProductBOMDAO.class);
	private final IProductBOMBL bomsService = Services.get(IProductBOMBL.class);

	@Nullable
	private final BOMUse bomToUse;

	@NonNull
	private final OrderLineCandidate initialCandidate;

	@NonNull
	@Singular
	private final List<BOMComponentType> explodeOnlyComponentTypes;

	public BOMExploderCommand(@Nullable final BOMUse bomToUse,
			final @NonNull OrderLineCandidate initialCandidate,
			@Nullable @Singular final List<BOMComponentType> explodeOnlyComponentTypes)
	{
		this.bomToUse = bomToUse;
		this.initialCandidate = initialCandidate;
		this.explodeOnlyComponentTypes = explodeOnlyComponentTypes == null ? Collections.emptyList() : ImmutableList.copyOf(explodeOnlyComponentTypes);
	}

	/**
	 * @return initial candidate if the initial product is not a BOM
	 */
	@NonNull
	public List<OrderLineCandidate> execute()
	{

		final ProductId bomProductId = initialCandidate.getProductId();
		final I_PP_Product_BOM bom = bomsRepo.getDefaultBOMByProductId(bomProductId).orElse(null);
		if (bom == null)
		{
			return ImmutableList.of(initialCandidate);
		}

		final BOMUse bomUse = BOMUse.ofNullableCode(bom.getBOMUse());
		if (bomToUse != null && !Objects.equals(bomToUse, bomUse))
		{
			return ImmutableList.of(initialCandidate);
		}

		GroupId compensationGroupId = null;

		final ArrayList<OrderLineCandidate> result = new ArrayList<>();
		final List<I_PP_Product_BOMLine> bomLines = bomsRepo.retrieveLines(bom);
		for (final I_PP_Product_BOMLine bomLine : bomLines)
		{
			final BOMComponentType bomLineComponentType = BOMComponentType.ofCode(bomLine.getComponentType());
			if (!explodeOnlyComponentTypes.contains(bomLineComponentType))
			{
				continue;
			}
			final ProductBOMLineId bomLineId = ProductBOMLineId.ofRepoId(bomLine.getPP_Product_BOMLine_ID());
			final ProductId bomLineProductId = ProductId.ofRepoId(bomLine.getM_Product_ID());
			final BigDecimal bomLineQty = bomsService.computeQtyRequired(bomLine, bomProductId, initialCandidate.getQty());
			final UomId bomUomId = UomId.ofRepoId(bomLine.getC_UOM_ID());

			final AttributeSetInstanceId bomLineAsiId = AttributeSetInstanceId.ofRepoIdOrNone(bomLine.getM_AttributeSetInstance_ID());
			final ImmutableAttributeSet attributes = asiBL.getImmutableAttributeSetById(bomLineAsiId);

			if (compensationGroupId == null)
			{
				compensationGroupId = orderGroupsRepo.createNewGroupId(GroupCreateRequest.builder()
						.orderId(initialCandidate.getOrderId())
						.name(productBL.getProductName(bomProductId))
						.build());
			}

			final OrderLineCandidate lineCandidate = initialCandidate.toBuilder()
					.productId(bomLineProductId)
					.attributes(attributes)
					.qty(bomLineQty)
					.compensationGroupId(compensationGroupId)
					.uomId(bomUomId)
					.explodedFromBOMLineId(bomLineId)
					.build();
			result.addAll(this.toBuilder()
					.initialCandidate(lineCandidate)
					.build()
					.execute());  // recurse
		}

		return result;
	}
}
