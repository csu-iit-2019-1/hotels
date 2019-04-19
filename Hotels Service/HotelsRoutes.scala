package com.example

import akka.actor.{ActorRef, ActorSystem}
import akka.event.Logging

import scala.concurrent.duration._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.model.{DateTime, StatusCodes}
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.directives.MethodDirectives.delete
import akka.http.scaladsl.server.directives.MethodDirectives.get
import akka.http.scaladsl.server.directives.MethodDirectives.post
import akka.http.scaladsl.server.directives.RouteDirectives.complete
import akka.http.scaladsl.server.directives.PathDirectives.path

import scala.concurrent.Future
import com.example.HotelsActor._
import akka.pattern.ask
import akka.util.Timeout

trait HotelsRoutes extends JsonSupport {

  implicit def system: ActorSystem

  lazy val log = Logging(system, classOf[HotelsRoutes])

  def hotelsActor: ActorRef

  implicit lazy val timeout = Timeout(5.seconds)

  lazy val hotelsRoutes: Route =
    pathPrefix("hotels") {
      concat (
        pathPrefix("cities") {        //GET /hotels/cities/{cityId}
          concat(
            path(Segment) { cityId =>
              concat(
                get {
                  val hotels: Future[Hotels] =
                    (hotelsActor ? GetAllHotels).mapTo[Hotels]
                  complete(hotels)
                }
              )
            }
          )
        },
        path(Segment) { date =>       //GET /hotels/{date}/{cityId}/{stars}
          concat(
            path(Segment) { cityId =>
              concat(
                path(Segment) { stars =>
                  concat(
                    get {
                      val hotels: Future[Hotels] =
                        (hotelsActor ? GetAvailableHotels(DateTime.fromIsoDateTimeString(date).get, cityId.toInt, stars.toInt)).mapTo[Hotels]
                      complete(hotels)
                    }
                  )
                }
              )
            }
          )
        },
        path(Segment) { hotelId =>
          concat(
            get {                             //GET /hotels/{hotelId}
              val hotel: Future[Hotel] =
                (hotelsActor ? GetHotel(hotelId.toInt)).mapTo[Hotel]
              complete(hotel)
            },
            put {                             //PUT /hotels/{hotelId}
              entity(as[Hotel]) { hotel =>
                val valueCreated: Future[ActionPerformed] = (hotelsActor ? PutHotel(hotelId.toInt, hotel)).mapTo[ActionPerformed]
                onSuccess(valueCreated) { performed =>
                  log.info("Putted hotel [{}]: {}", hotel.id, performed.description)
                  complete((StatusCodes.Created, performed))
                }
              }
            },
            delete {                           //DELETE /hotels/{hotelId}
              val hotelDeleted: Future[ActionPerformed] = (hotelsActor ? DeleteHotel(hotelId.toInt)).mapTo[ActionPerformed]
              onSuccess(hotelDeleted) { performed =>
                log.info("Deleted hotel [{}]: {}", hotelId, performed.description)
                complete((StatusCodes.OK, performed))
              }
            }
          )
        },
        pathPrefix("averagecost") {       //GET /hotels/averagemincost/{day}/{stars}/{cityId}
          concat(
            path(Segment) { day =>
              concat(
                path(Segment) { cityId =>
                  concat(
                    path(Segment) { stars =>
                      concat(
                        get {
                          val avgCost: Future[AverageMinCosts] =
                            (hotelsActor ? GetAverageMinCosts(DateTime.fromIsoDateTimeString(day).get, cityId.toInt, stars.toInt)).mapTo[AverageMinCosts]
                          complete(avgCost)
                        }
                      )
                    }
                  )
                }
              )
            }
          )
        },
        pathPrefix("booking") {       //POST /hotel/booking/{hotelId}
          concat(
            path(Segment) { hotelId =>
              concat(
                post {
                  entity(as[BookingDetails]) { bookingDetails =>
                    val createdBooking: Future[BookingResult] = (hotelsActor ? BookingHotel(hotelId.toInt, bookingDetails)).mapTo[BookingResult]
                    onSuccess(createdBooking) { createdBooking =>
                      log.info("Booked [{}]: {}", createdBooking.id, createdBooking.status)
                      complete(createdBooking)  //return bookingId and status
                    }
                  }
                }
              )
            }
          )
        },
        pathPrefix("buyout") {        //PUT /hotels/buyout
          concat(
            put {
              entity(as[Int]) { bookingId =>
                val buyOutStatus: Future[ActionPerformed] = (hotelsActor ? BuyoutBooking(bookingId)).mapTo[ActionPerformed]
                onSuccess(buyOutStatus) { performed =>
                  log.info("Booking [{}]: {}", bookingId, performed.description)  //may be need to detail status
                  complete((StatusCodes.Created, performed))
                }
              }
            }
          )
        }
      )
    }

}