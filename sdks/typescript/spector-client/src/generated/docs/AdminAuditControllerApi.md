# AdminAuditControllerApi

All URIs are relative to *http://localhost*

| Method | HTTP request | Description |
|------------- | ------------- | -------------|
| [**queryAuditEvents**](AdminAuditControllerApi.md#queryauditevents) | **GET** /api/v1/admin/audit |  |
| [**verifyAuditChain**](AdminAuditControllerApi.md#verifyauditchain) | **GET** /api/v1/admin/audit/verify |  |



## queryAuditEvents

> Array&lt;AuditEventDto&gt; queryAuditEvents(tenantId, actorId, actorAccountId, action, targetType, targetId, namespaceId, outcome, from, to, limit)



### Example

```ts
import {
  Configuration,
  AdminAuditControllerApi,
} from '';
import type { QueryAuditEventsRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // To configure API key authorization: ApiKeyAuth
    apiKey: "YOUR API KEY",
    // Configure HTTP bearer authorization: BearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new AdminAuditControllerApi(config);

  const body = {
    // string (optional)
    tenantId: tenantId_example,
    // string (optional)
    actorId: actorId_example,
    // string (optional)
    actorAccountId: actorAccountId_example,
    // string (optional)
    action: action_example,
    // string (optional)
    targetType: targetType_example,
    // string (optional)
    targetId: targetId_example,
    // string (optional)
    namespaceId: namespaceId_example,
    // string (optional)
    outcome: outcome_example,
    // Date (optional)
    from: 2013-10-20T19:20:30+01:00,
    // Date (optional)
    to: 2013-10-20T19:20:30+01:00,
    // number (optional)
    limit: 56,
  } satisfies QueryAuditEventsRequest;

  try {
    const data = await api.queryAuditEvents(body);
    console.log(data);
  } catch (error) {
    console.error(error);
  }
}

// Run the test
example().catch(console.error);
```

### Parameters


| Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **tenantId** | `string` |  | [Optional] [Defaults to `undefined`] |
| **actorId** | `string` |  | [Optional] [Defaults to `undefined`] |
| **actorAccountId** | `string` |  | [Optional] [Defaults to `undefined`] |
| **action** | `string` |  | [Optional] [Defaults to `undefined`] |
| **targetType** | `string` |  | [Optional] [Defaults to `undefined`] |
| **targetId** | `string` |  | [Optional] [Defaults to `undefined`] |
| **namespaceId** | `string` |  | [Optional] [Defaults to `undefined`] |
| **outcome** | `string` |  | [Optional] [Defaults to `undefined`] |
| **from** | `Date` |  | [Optional] [Defaults to `undefined`] |
| **to** | `Date` |  | [Optional] [Defaults to `undefined`] |
| **limit** | `number` |  | [Optional] [Defaults to `undefined`] |

### Return type

[**Array&lt;AuditEventDto&gt;**](AuditEventDto.md)

### Authorization

[ApiKeyAuth](../README.md#ApiKeyAuth), [BearerAuth](../README.md#BearerAuth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: `application/json`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | OK |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## verifyAuditChain

> AuditChainVerificationDto verifyAuditChain(tenantId)



### Example

```ts
import {
  Configuration,
  AdminAuditControllerApi,
} from '';
import type { VerifyAuditChainRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // To configure API key authorization: ApiKeyAuth
    apiKey: "YOUR API KEY",
    // Configure HTTP bearer authorization: BearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new AdminAuditControllerApi(config);

  const body = {
    // string (optional)
    tenantId: tenantId_example,
  } satisfies VerifyAuditChainRequest;

  try {
    const data = await api.verifyAuditChain(body);
    console.log(data);
  } catch (error) {
    console.error(error);
  }
}

// Run the test
example().catch(console.error);
```

### Parameters


| Name | Type | Description  | Notes |
|------------- | ------------- | ------------- | -------------|
| **tenantId** | `string` |  | [Optional] [Defaults to `undefined`] |

### Return type

[**AuditChainVerificationDto**](AuditChainVerificationDto.md)

### Authorization

[ApiKeyAuth](../README.md#ApiKeyAuth), [BearerAuth](../README.md#BearerAuth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: `application/json`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | OK |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)

