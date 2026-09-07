
# UpdateCredentialRequest


## Properties

Name | Type
------------ | -------------
`category` | string
`provider` | string
`credentialType` | string
`secret` | string
`properties` | { [key: string]: any | null; }
`isDefault` | boolean
`description` | string
`expiresAt` | Date

## Example

```typescript
import type { UpdateCredentialRequest } from ''

// TODO: Update the object below with actual values
const example = {
  "category": null,
  "provider": null,
  "credentialType": null,
  "secret": null,
  "properties": null,
  "isDefault": null,
  "description": null,
  "expiresAt": null,
} satisfies UpdateCredentialRequest

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as UpdateCredentialRequest
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


