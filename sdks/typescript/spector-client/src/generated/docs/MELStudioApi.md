# MELStudioApi

All URIs are relative to *http://localhost*

| Method | HTTP request | Description |
|------------- | ------------- | -------------|
| [**execute**](MELStudioApi.md#execute) | **POST** /api/v1/mel/execute | Execute MEL script |
| [**schema**](MELStudioApi.md#schema) | **GET** /api/v1/mel/schema | Get MEL language schema |
| [**validate**](MELStudioApi.md#validate) | **POST** /api/v1/mel/validate | Validate MEL script syntax |



## execute

> MelExecuteResponse execute(melExecuteRequest)

Execute MEL script

Executes one or more MEL statements bound to the caller\&#39;s namespace

### Example

```ts
import {
  Configuration,
  MELStudioApi,
} from '';
import type { ExecuteRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // To configure API key authorization: ApiKeyAuth
    apiKey: "YOUR API KEY",
    // Configure HTTP bearer authorization: BearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new MELStudioApi(config);

  const body = {
    // MelExecuteRequest
    melExecuteRequest: ...,
  } satisfies ExecuteRequest;

  try {
    const data = await api.execute(body);
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
| **melExecuteRequest** | [MelExecuteRequest](MelExecuteRequest.md) |  | |

### Return type

[**MelExecuteResponse**](MelExecuteResponse.md)

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


## schema

> MelSchemaResponse schema()

Get MEL language schema

Returns MEL language keywords, tiers, sources, clause templates, and snippet library definitions

### Example

```ts
import {
  Configuration,
  MELStudioApi,
} from '';
import type { SchemaRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // To configure API key authorization: ApiKeyAuth
    apiKey: "YOUR API KEY",
    // Configure HTTP bearer authorization: BearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new MELStudioApi(config);

  try {
    const data = await api.schema();
    console.log(data);
  } catch (error) {
    console.error(error);
  }
}

// Run the test
example().catch(console.error);
```

### Parameters

This endpoint does not need any parameter.

### Return type

[**MelSchemaResponse**](MelSchemaResponse.md)

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


## validate

> MelValidateResponse validate(melValidateRequest)

Validate MEL script syntax

Validates MEL script syntax and returns exact line/column diagnostics for editor squiggles

### Example

```ts
import {
  Configuration,
  MELStudioApi,
} from '';
import type { ValidateRequest } from '';

async function example() {
  console.log("🚀 Testing  SDK...");
  const config = new Configuration({ 
    // To configure API key authorization: ApiKeyAuth
    apiKey: "YOUR API KEY",
    // Configure HTTP bearer authorization: BearerAuth
    accessToken: "YOUR BEARER TOKEN",
  });
  const api = new MELStudioApi(config);

  const body = {
    // MelValidateRequest
    melValidateRequest: ...,
  } satisfies ValidateRequest;

  try {
    const data = await api.validate(body);
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
| **melValidateRequest** | [MelValidateRequest](MelValidateRequest.md) |  | |

### Return type

[**MelValidateResponse**](MelValidateResponse.md)

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

