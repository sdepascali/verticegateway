/* 
** Copyright [2012-2013] [Megam Systems]
**
** Licensed under the Apache License, Version 2.0 (the "License");
** you may not use this file except in compliance with the License.
** You may obtain a copy of the License at
**
** http://www.apache.org/licenses/LICENSE-2.0
**
** Unless required by applicable law or agreed to in writing, software
** distributed under the License is distributed on an "AS IS" BASIS,
** WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
** See the License for the specific language governing permissions and
** limitations under the License.
*/
import scalaz._
import Scalaz._
import scalaz.NonEmptyList
import scalaz.NonEmptyList._


/**
 * @author rajthilak
 *
 */
package object models {
  type NodeResults = NonEmptyList[Option[NodeResult]]

  object NodeResults {
    def apply(m: NodeResult): NodeResults = nels(m.some)    
    def empty: NodeResults = nels(none)
  }
  
  type PredefsResults = NonEmptyList[Option[PredefsResult]]
  
  object PredefsResults {
    def apply(m: PredefsResult): PredefsResults = nels(m.some)
    def empty: PredefsResults = nels(none)
    
  }
  
  type PredefCloudResults = NonEmptyList[Option[PredefCloudResult]]
  
  object PredefCloudResults {
    def apply(m: PredefCloudResult): PredefCloudResults = nels(m.some)
    def empty: PredefCloudResults = nels(none)
    
  }
}