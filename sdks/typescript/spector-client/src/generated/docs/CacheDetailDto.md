
# CacheDetailDto


## Properties

Name | Type
------------ | -------------
`name` | string
`estimatedSize` | number
`hitCount` | number
`missCount` | number
`hitRate` | number
`evictionCount` | number
`keys` | Set&lt;any&gt;

## Example

```typescript
import type { CacheDetailDto } from ''

// TODO: Update the object below with actual values
const example = {
  "name": null,
  "estimatedSize": null,
  "hitCount": null,
  "missCount": null,
  "hitRate": null,
  "evictionCount": null,
  "keys": null,
} satisfies CacheDetailDto

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as CacheDetailDto
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


