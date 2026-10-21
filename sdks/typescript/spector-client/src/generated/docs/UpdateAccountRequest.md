
# UpdateAccountRequest


## Properties

Name | Type
------------ | -------------
`displayName` | string
`email` | string
`active` | boolean
`profile` | string
`roles` | Set&lt;string&gt;
`scopes` | Set&lt;string&gt;
`legalHold` | boolean
`ownerAccountId` | string
`maxNamespaces` | number
`maxHotNamespaces` | number

## Example

```typescript
import type { UpdateAccountRequest } from ''

// TODO: Update the object below with actual values
const example = {
  "displayName": null,
  "email": null,
  "active": null,
  "profile": null,
  "roles": null,
  "scopes": null,
  "legalHold": null,
  "ownerAccountId": null,
  "maxNamespaces": null,
  "maxHotNamespaces": null,
} satisfies UpdateAccountRequest

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as UpdateAccountRequest
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


