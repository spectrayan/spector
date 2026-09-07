
# RegisterRequest


## Properties

Name | Type
------------ | -------------
`username` | string
`password` | string
`email` | string
`displayName` | string
`roles` | Set&lt;string&gt;
`scopes` | Set&lt;string&gt;
`mustChangePassword` | boolean

## Example

```typescript
import type { RegisterRequest } from ''

// TODO: Update the object below with actual values
const example = {
  "username": null,
  "password": null,
  "email": null,
  "displayName": null,
  "roles": null,
  "scopes": null,
  "mustChangePassword": null,
} satisfies RegisterRequest

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as RegisterRequest
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


