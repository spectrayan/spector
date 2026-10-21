
# TenantKeyRevocationResponse


## Properties

Name | Type
------------ | -------------
`tenantId` | string
`status` | string
`healthStatus` | string
`revokedNamespaceDekCount` | number
`evictedCachedDeks` | number
`evictedMemoryInstances` | number
`reason` | string
`revokedAt` | Date

## Example

```typescript
import type { TenantKeyRevocationResponse } from ''

// TODO: Update the object below with actual values
const example = {
  "tenantId": null,
  "status": null,
  "healthStatus": null,
  "revokedNamespaceDekCount": null,
  "evictedCachedDeks": null,
  "evictedMemoryInstances": null,
  "reason": null,
  "revokedAt": null,
} satisfies TenantKeyRevocationResponse

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as TenantKeyRevocationResponse
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


