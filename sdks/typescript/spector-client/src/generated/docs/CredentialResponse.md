
# CredentialResponse


## Properties

Name | Type
------------ | -------------
`credentialId` | string
`tenantId` | string
`userId` | string
`name` | string
`category` | string
`provider` | string
`credentialType` | string
`maskedPreview` | string
`properties` | { [key: string]: any | null; }
`isDefault` | boolean
`description` | string
`version` | number
`createdAt` | Date
`updatedAt` | Date
`expiresAt` | Date
`lastUsedAt` | Date

## Example

```typescript
import type { CredentialResponse } from ''

// TODO: Update the object below with actual values
const example = {
  "credentialId": null,
  "tenantId": null,
  "userId": null,
  "name": null,
  "category": null,
  "provider": null,
  "credentialType": null,
  "maskedPreview": null,
  "properties": null,
  "isDefault": null,
  "description": null,
  "version": null,
  "createdAt": null,
  "updatedAt": null,
  "expiresAt": null,
  "lastUsedAt": null,
} satisfies CredentialResponse

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as CredentialResponse
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


