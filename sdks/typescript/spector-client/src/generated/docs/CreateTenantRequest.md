
# CreateTenantRequest


## Properties

Name | Type
------------ | -------------
`tenantId` | string
`name` | string
`description` | string
`plan` | string
`status` | string
`legalHold` | boolean
`settings` | [TenantSettingsDto](TenantSettingsDto.md)
`quotas` | [TenantQuotasDto](TenantQuotasDto.md)

## Example

```typescript
import type { CreateTenantRequest } from ''

// TODO: Update the object below with actual values
const example = {
  "tenantId": null,
  "name": null,
  "description": null,
  "plan": null,
  "status": null,
  "legalHold": null,
  "settings": null,
  "quotas": null,
} satisfies CreateTenantRequest

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as CreateTenantRequest
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


