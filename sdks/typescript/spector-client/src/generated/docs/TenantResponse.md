
# TenantResponse


## Properties

Name | Type
------------ | -------------
`tenantId` | string
`name` | string
`description` | string
`status` | string
`plan` | string
`legalHold` | boolean
`settings` | [TenantSettingsDto](TenantSettingsDto.md)
`quotas` | [TenantQuotasDto](TenantQuotasDto.md)
`accountCount` | number
`namespaceCount` | number
`storageBytes` | number
`createdAt` | Date
`updatedAt` | Date
`deletedAt` | Date

## Example

```typescript
import type { TenantResponse } from ''

// TODO: Update the object below with actual values
const example = {
  "tenantId": null,
  "name": null,
  "description": null,
  "status": null,
  "plan": null,
  "legalHold": null,
  "settings": null,
  "quotas": null,
  "accountCount": null,
  "namespaceCount": null,
  "storageBytes": null,
  "createdAt": null,
  "updatedAt": null,
  "deletedAt": null,
} satisfies TenantResponse

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as TenantResponse
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


