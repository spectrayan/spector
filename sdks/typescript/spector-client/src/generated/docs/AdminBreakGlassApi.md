# AdminBreakGlassApi

All URIs are relative to *http://localhost*

| Method | HTTP request | Description |
|------------- | ------------- | -------------|
| [**approveBreakGlass**](AdminBreakGlassApi.md#approvebreakglassoperation) | **POST** /api/v1/admin/break-glass/{id}/approve | Approve a pending break-glass request (requires distinct second admin) |
| [**getBreakGlassRequest**](AdminBreakGlassApi.md#getbreakglassrequest) | **GET** /api/v1/admin/break-glass/{id} | Get break-glass session details by ID |
| [**listBreakGlassRequests**](AdminBreakGlassApi.md#listbreakglassrequests) | **GET** /api/v1/admin/break-glass | List break-glass requests and sessions |
| [**rejectBreakGlass**](AdminBreakGlassApi.md#rejectbreakglassoperation) | **POST** /api/v1/admin/break-glass/{id}/reject | Reject a pending break-glass request |
| [**requestBreakGlass**](AdminBreakGlassApi.md#requestbreakglass) | **POST** /api/v1/admin/break-glass | Submit a time-boxed emergency break-glass access request |
| [**revokeBreakGlass**](AdminBreakGlassApi.md#revokebreakglassoperation) | **POST** /api/v1/admin/break-glass/{id}/revoke | Manually revoke an active or pending break-glass session |



## approveBreakGlass

> BreakGlassResponse approveBreakGlass(id, approveBreakGlassRequest)

Approve a pending break-glass request (requires distinct second admin)

### Example

```ts
import {
  Configuration,
  AdminBreakGlassApi,
} from '';
import type { ApproveBreakGlassOperationRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // To configure API key authorization: ApiKeyAuth
    apiKey: "YOUR API KEY",
    // Configure HTTP bearer authorization: BearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new AdminBreakGlassApi(config);

  const body = {
    // string
    id: id_example,
    // ApproveBreakGlassRequest (optional)
    approveBreakGlassRequest: ...,
  } satisfies ApproveBreakGlassOperationRequest;

  try {
    const data = await api.approveBreakGlass(body);
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
| **id** | `string` |  | [Defaults to `undefined`] |
| **approveBreakGlassRequest** | [ApproveBreakGlassRequest](ApproveBreakGlassRequest.md) |  | [Optional] |

### Return type

[**BreakGlassResponse**](BreakGlassResponse.md)

### Authorization

[ApiKeyAuth](../README.md#ApiKeyAuth), [BearerAuth](../README.md#BearerAuth)

### HTTP request headers

- **Content-Type**: `application/json`
- **Accept**: `application/json`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | OK |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## getBreakGlassRequest

> BreakGlassResponse getBreakGlassRequest(id)

Get break-glass session details by ID

### Example

```ts
import {
  Configuration,
  AdminBreakGlassApi,
} from '';
import type { GetBreakGlassRequestRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // To configure API key authorization: ApiKeyAuth
    apiKey: "YOUR API KEY",
    // Configure HTTP bearer authorization: BearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new AdminBreakGlassApi(config);

  const body = {
    // string
    id: id_example,
  } satisfies GetBreakGlassRequestRequest;

  try {
    const data = await api.getBreakGlassRequest(body);
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
| **id** | `string` |  | [Defaults to `undefined`] |

### Return type

[**BreakGlassResponse**](BreakGlassResponse.md)

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


## listBreakGlassRequests

> Array&lt;BreakGlassResponse&gt; listBreakGlassRequests(tenantId, namespaceId, status)

List break-glass requests and sessions

### Example

```ts
import {
  Configuration,
  AdminBreakGlassApi,
} from '';
import type { ListBreakGlassRequestsRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // To configure API key authorization: ApiKeyAuth
    apiKey: "YOUR API KEY",
    // Configure HTTP bearer authorization: BearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new AdminBreakGlassApi(config);

  const body = {
    // string (optional)
    tenantId: tenantId_example,
    // string (optional)
    namespaceId: namespaceId_example,
    // string (optional)
    status: status_example,
  } satisfies ListBreakGlassRequestsRequest;

  try {
    const data = await api.listBreakGlassRequests(body);
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
| **namespaceId** | `string` |  | [Optional] [Defaults to `undefined`] |
| **status** | `string` |  | [Optional] [Defaults to `undefined`] |

### Return type

[**Array&lt;BreakGlassResponse&gt;**](BreakGlassResponse.md)

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


## rejectBreakGlass

> BreakGlassResponse rejectBreakGlass(id, rejectBreakGlassRequest)

Reject a pending break-glass request

### Example

```ts
import {
  Configuration,
  AdminBreakGlassApi,
} from '';
import type { RejectBreakGlassOperationRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // To configure API key authorization: ApiKeyAuth
    apiKey: "YOUR API KEY",
    // Configure HTTP bearer authorization: BearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new AdminBreakGlassApi(config);

  const body = {
    // string
    id: id_example,
    // RejectBreakGlassRequest (optional)
    rejectBreakGlassRequest: ...,
  } satisfies RejectBreakGlassOperationRequest;

  try {
    const data = await api.rejectBreakGlass(body);
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
| **id** | `string` |  | [Defaults to `undefined`] |
| **rejectBreakGlassRequest** | [RejectBreakGlassRequest](RejectBreakGlassRequest.md) |  | [Optional] |

### Return type

[**BreakGlassResponse**](BreakGlassResponse.md)

### Authorization

[ApiKeyAuth](../README.md#ApiKeyAuth), [BearerAuth](../README.md#BearerAuth)

### HTTP request headers

- **Content-Type**: `application/json`
- **Accept**: `application/json`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | OK |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## requestBreakGlass

> BreakGlassResponse requestBreakGlass(createBreakGlassRequest)

Submit a time-boxed emergency break-glass access request

### Example

```ts
import {
  Configuration,
  AdminBreakGlassApi,
} from '';
import type { RequestBreakGlassRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // To configure API key authorization: ApiKeyAuth
    apiKey: "YOUR API KEY",
    // Configure HTTP bearer authorization: BearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new AdminBreakGlassApi(config);

  const body = {
    // CreateBreakGlassRequest
    createBreakGlassRequest: ...,
  } satisfies RequestBreakGlassRequest;

  try {
    const data = await api.requestBreakGlass(body);
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
| **createBreakGlassRequest** | [CreateBreakGlassRequest](CreateBreakGlassRequest.md) |  | |

### Return type

[**BreakGlassResponse**](BreakGlassResponse.md)

### Authorization

[ApiKeyAuth](../README.md#ApiKeyAuth), [BearerAuth](../README.md#BearerAuth)

### HTTP request headers

- **Content-Type**: `application/json`
- **Accept**: `application/json`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | OK |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


## revokeBreakGlass

> BreakGlassResponse revokeBreakGlass(id, revokeBreakGlassRequest)

Manually revoke an active or pending break-glass session

### Example

```ts
import {
  Configuration,
  AdminBreakGlassApi,
} from '';
import type { RevokeBreakGlassOperationRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // To configure API key authorization: ApiKeyAuth
    apiKey: "YOUR API KEY",
    // Configure HTTP bearer authorization: BearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new AdminBreakGlassApi(config);

  const body = {
    // string
    id: id_example,
    // RevokeBreakGlassRequest (optional)
    revokeBreakGlassRequest: ...,
  } satisfies RevokeBreakGlassOperationRequest;

  try {
    const data = await api.revokeBreakGlass(body);
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
| **id** | `string` |  | [Defaults to `undefined`] |
| **revokeBreakGlassRequest** | [RevokeBreakGlassRequest](RevokeBreakGlassRequest.md) |  | [Optional] |

### Return type

[**BreakGlassResponse**](BreakGlassResponse.md)

### Authorization

[ApiKeyAuth](../README.md#ApiKeyAuth), [BearerAuth](../README.md#BearerAuth)

### HTTP request headers

- **Content-Type**: `application/json`
- **Accept**: `application/json`


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | OK |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)

