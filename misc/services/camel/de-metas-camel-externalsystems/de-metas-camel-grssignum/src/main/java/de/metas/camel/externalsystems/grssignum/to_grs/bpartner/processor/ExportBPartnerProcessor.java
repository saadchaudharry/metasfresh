/*
 * #%L
 * de-metas-camel-grssignum
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

package de.metas.camel.externalsystems.grssignum.to_grs.bpartner.processor;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import de.metas.camel.externalsystems.common.JsonObjectMapperHolder;
import de.metas.camel.externalsystems.common.ProcessorHelper;
import de.metas.camel.externalsystems.grssignum.GRSSignumConstants;
import de.metas.camel.externalsystems.grssignum.from_grs.restapi.Endpoint;
import de.metas.camel.externalsystems.grssignum.to_grs.api.model.JsonBPartner;
import de.metas.camel.externalsystems.grssignum.to_grs.api.model.JsonBPartnerContact;
import de.metas.camel.externalsystems.grssignum.to_grs.api.model.JsonBPartnerContactRole;
import de.metas.camel.externalsystems.grssignum.to_grs.bpartner.ExportBPartnerRouteContext;
import de.metas.camel.externalsystems.grssignum.to_grs.client.model.DispatchRequest;
import de.metas.common.bpartner.v2.response.JsonResponseBPartner;
import de.metas.common.bpartner.v2.response.JsonResponseComposite;
import de.metas.common.bpartner.v2.response.JsonResponseContact;
import de.metas.common.bpartner.v2.response.JsonResponseContactRole;
import de.metas.common.bpartner.v2.response.JsonResponseLocation;
import de.metas.common.util.Check;
import lombok.NonNull;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;

import javax.annotation.Nullable;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ExportBPartnerProcessor implements Processor
{
	@Override
	public void process(final Exchange exchange) throws Exception
	{
		final ExportBPartnerRouteContext routeContext = ProcessorHelper.getPropertyOrThrowError(exchange, GRSSignumConstants.ROUTE_PROPERTY_EXPORT_BPARTNER_CONTEXT, ExportBPartnerRouteContext.class);

		final JsonResponseComposite jsonResponseComposite = exchange.getIn().getBody(JsonResponseComposite.class);

		final JsonBPartner jsonBPartnerToExport = toJsonBPartner(jsonResponseComposite, routeContext.getTenantId());

		final DispatchRequest dispatchRequest = DispatchRequest.builder()
				.url(routeContext.getRemoteUrl())
				.authToken(routeContext.getAuthToken())
				.request(JsonObjectMapperHolder.sharedJsonObjectMapper().writeValueAsString(jsonBPartnerToExport))
				.build();

		exchange.getIn().setBody(dispatchRequest);
	}

	@NonNull
	private JsonBPartner toJsonBPartner(@NonNull final JsonResponseComposite jsonResponseComposite, @NonNull final String tenantId)
	{
		final JsonResponseBPartner jsonResponseBPartner = jsonResponseComposite.getBpartner();

		final String bpartnerMetasfreshId = String.valueOf(jsonResponseBPartner.getMetasfreshId().getValue());

		final String bPartnerName = computeBPartnerNameToExport(jsonResponseBPartner);

		final int inactive = jsonResponseBPartner.isActive() ? 0 : 1;

		final JsonBPartner.JsonBPartnerBuilder bPartnerBuilder = getBPartnerLocationToExport(jsonResponseComposite.getLocations())
				.map(ExportBPartnerProcessor::initBPartnerWithLocationFields)
				.orElseGet(JsonBPartner::builder);

		return bPartnerBuilder
				.id(bpartnerMetasfreshId)
				.name(bPartnerName)
				.inactive(inactive)
				.flag(Endpoint.BPARTNER.getFlag())
				.tenantId(tenantId)
				.name2(jsonResponseBPartner.getName2())
				.contacts(toJsonBPartnerContact(jsonResponseComposite.getContacts()))
				.build();
	}

	@NonNull
	private String computeBPartnerNameToExport(@NonNull final JsonResponseBPartner jsonResponseBPartner)
	{
		final String bpartnerName = Stream.of(jsonResponseBPartner.getName(),
											  jsonResponseBPartner.getName2(),
											  jsonResponseBPartner.getName3())
				.filter(Check::isNotBlank)
				.collect(Collectors.joining(" "));

		if (Check.isBlank(bpartnerName))
		{
			throw new RuntimeException("BPartner with metasfreshId: " + jsonResponseBPartner.getMetasfreshId().getValue() + " missing name information!");
		}

		return bpartnerName;
	}

	@NonNull
	private static List<JsonBPartnerContact> toJsonBPartnerContact(@NonNull final List<JsonResponseContact> contacts)
	{
		return contacts.stream()
				.map(contact -> JsonBPartnerContact.builder()
						.metasfreshId(contact.getMetasfreshId())
						.firstName(contact.getFirstName())
						.lastName(contact.getLastName())
						.email(contact.getEmail())
						.fax(contact.getFax())
						.greeting(contact.getGreeting())
						.title(contact.getTitle())
						.phone(contact.getPhone())
						.phone2(contact.getPhone2())
						.contactRoles(toJsonBPartnerContactRole(contact.getRoles()))
						.position(contact.getPosition() == null ? null : contact.getPosition().getName())
						.build())
				.collect(ImmutableList.toImmutableList());
	}

	@NonNull
	@VisibleForTesting
	public static Optional<JsonResponseLocation> getBPartnerLocationToExport(@NonNull final List<JsonResponseLocation> jsonResponseLocations)
	{
		if (Check.isEmpty(jsonResponseLocations))
		{
			return Optional.empty();
		}

		return jsonResponseLocations.stream()
				.min(Comparator.comparing(JsonResponseLocation::isVisitorsAddress, Comparator.reverseOrder())
							 .thenComparing(JsonResponseLocation::isBillToDefault, Comparator.reverseOrder())
							 .thenComparing(JsonResponseLocation::isShipToDefault, Comparator.reverseOrder())
							 .thenComparing(JsonResponseLocation::isBillTo, Comparator.reverseOrder())
							 .thenComparing(JsonResponseLocation::isShipTo, Comparator.reverseOrder()));
	}

	@Nullable
	private static List<JsonBPartnerContactRole> toJsonBPartnerContactRole(@Nullable final List<JsonResponseContactRole> roles)
	{
		if (Check.isEmpty(roles))
		{
			return null;
		}

		return roles.stream()
				.map(role -> JsonBPartnerContactRole.builder()
						.role(role.getName())
						.build())
				.collect(Collectors.toList());
	}

	@NonNull
	private static JsonBPartner.JsonBPartnerBuilder initBPartnerWithLocationFields(@NonNull final JsonResponseLocation jsonResponseLocation)
	{
		return JsonBPartner.builder()
			.address1(jsonResponseLocation.getAddress1())
			.address2(jsonResponseLocation.getAddress2())
			.address3(jsonResponseLocation.getAddress3())
			.address4(jsonResponseLocation.getAddress4())
			.postal(jsonResponseLocation.getPostal())
			.city(jsonResponseLocation.getCity())
			.countryCode(jsonResponseLocation.getCountryCode())
			.gln(jsonResponseLocation.getGln());
	}
}
