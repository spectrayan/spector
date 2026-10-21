
# CreateAccountRequest


## Properties

Name | Type
------------ | -------------
`username` | string
`password` | string
`displayName` | string
`email` | string
`tenantId` | string
`kind` | string
`profile` | string
`roles` | Set&lt;string&gt;
`scopes` | Set&lt;string&gt;
`mustChangePassword` | boolean
`legalHold` | boolean
`ownerAccountId` | string
`maxNamespaces` | number
`maxHotNamespaces` | number
`initialGrants` | [Array&lt;InitialNamespaceGrantDto&gt;](InitialNamespaceGrantDto.md)

## Example

```typescript
import type { CreateAccountRequest } from ''

// TODO: Update the object below with actual values
const example = {
  "username": null,
  "password": null,
  "displayName": null,
  "email": null,
  "tenantId": null,
  "kind": null,
  "profile": null,
  "roles": null,
  "scopes": null,
  "mustChangePassword": null,
  "legalHold": null,
  "ownerAccountId": null,
  "maxNamespaces": null,
  "maxHotNamespaces": null,
  "initialGrants": null,
} satisfies CreateAccountRequest

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as CreateAccountRequest
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


