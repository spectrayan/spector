
# TenantKeyValidationResponse


## Properties

Name | Type
------------ | -------------
`tenantId` | string
`provider` | string
`kekKeyId` | string
`valid` | boolean
`healthStatus` | string
`roundTripVerified` | boolean
`evictedCachedDeks` | number
`verifiedAt` | Date
`errorCode` | string
`message` | string

## Example

```typescript
import type { TenantKeyValidationResponse } from ''

// TODO: Update the object below with actual values
const example = {
  "tenantId": null,
  "provider": null,
  "kekKeyId": null,
  "valid": null,
  "healthStatus": null,
  "roundTripVerified": null,
  "evictedCachedDeks": null,
  "verifiedAt": null,
  "errorCode": null,
  "message": null,
} satisfies TenantKeyValidationResponse

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as TenantKeyValidationResponse
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


